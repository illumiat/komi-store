package zed.rainxch.core.presentation.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val TAG = "AppIconCache"

/** How many icons stay resident. The library screen shows ~22 at a time; this leaves headroom. */
private const val ICON_CACHE_SIZE = 48

/**
 * The largest size any caller draws an installed-app icon at, in dp. `AppItemCard` uses 64dp; the
 * other call sites use 48, 36 and 16. See [AppIconCache]'s implementation for why icons are
 * rasterised at this size rather than the drawable's intrinsic one.
 */
private const val MAX_ICON_DP = 64

private val instanceLock = Any()

@Volatile
private var instance: AndroidAppIconCache? = null

actual fun appIconCache(context: Context): AppIconCache =
    instance ?: synchronized(instanceLock) {
        instance ?: AndroidAppIconCache(context.applicationContext).also { instance = it }
    }

/**
 * Process-wide [AppIconCache] for Android.
 *
 * Two things make this more than a map:
 *
 * 1. **Resolution never happens on the calling thread.** Both `PackageManager` lookups and APK
 *    parsing touch disk; doing them during composition stalls the frame currently being drawn.
 * 2. **Concurrent callers share one resolution.** A grid that shows twenty-two cards at once asks
 *    for twenty-two icons in the same frame, so without collapsing they would race on the same
 *    package and each parse the same archive.
 *
 * Each key owns its own [MutableStateFlow], which is what makes a watcher's view consistent: the
 * value is readable at any moment and a subscriber always receives the current one, so a result
 * landing between "start resolving" and "subscribe" cannot be missed. Pushing keys through a single
 * `SharedFlow` without replay left exactly that window open.
 *
 * Icons are dropped when the package behind them changes, so an app updated while the process is
 * alive does not keep showing its previous icon.
 */
private class AndroidAppIconCache(private val context: Context) : AppIconCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Rasterisation target, derived once from the display density. See [toTargetSize]. */
    private val targetIconPx: Int =
        (MAX_ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    /**
     * Guards [cache], [inFlight] and [generations] together.
     *
     * They are only meaningful as one thing - what is cached, what is being resolved, and which
     * package generation each belongs to - so invalidation and resolution have to agree on all
     * three. Splitting them across separate locks is what let a key be invisible to an
     * invalidation while it was being resolved.
     */
    private val lock = Any()

    private val cache =
        object : LinkedHashMap<AppIconCache.Key, ImageBitmap>(ICON_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AppIconCache.Key, ImageBitmap>) =
                size > ICON_CACHE_SIZE
        }

    private val inFlight = HashMap<AppIconCache.Key, Job>()

    /**
     * Bumped per package whenever a package event says its state moved. A resolution carries the
     * generation it started under, so one reading the old package state can be told to keep its
     * result out of the cache.
     */
    private val generations = HashMap<String, Long>()

    /**
     * The current icon per watched key; see the class comment for why this is the watch channel.
     *
     * Known cost: an entry is never dropped, and a `MutableStateFlow` holds its value strongly, so
     * a key watched once keeps its bitmap reachable even after [cache]'s LRU evicts it - the
     * effective floor on memory is "keys ever watched", not [ICON_CACHE_SIZE]. Bounding it by
     * subscription count was tried and reverted: `observe` hands the flow to the caller, so "in use"
     * becomes true when it is returned, while `onStart` only fires when collection begins - a
     * collector that has not started yet looks idle, its entry gets dropped, the writer keeps the
     * old flow object, and that screen never shows an icon again. Bounding this properly means
     * changing what `observe` returns, not adding a counter here.
     */
    private val states = ConcurrentHashMap<AppIconCache.Key, MutableStateFlow<ImageBitmap?>>()

    init {
        registerPackageChangeListener()
    }

    override fun get(key: AppIconCache.Key): ImageBitmap? = synchronized(lock) { cache[key] }

    /**
     * Watches one icon, starting a resolution if there is nothing cached yet.
     *
     * The resolve is essential, not an optimisation: `stateFor` seeds its flow from the cache, so
     * without it a key that has never been resolved would emit `null` forever and the caller would
     * sit on the placeholder. The dedup inside [request] is what makes it safe for several
     * observers to ask for the same key in the same frame.
     */
    override fun observe(key: AppIconCache.Key): Flow<ImageBitmap?> {
        val state = stateFor(key)
        request(key, state)
        return state.asStateFlow()
    }

    /**
     * Returns the flow for [key], creating it seeded from the cache if this is the first watch.
     *
     * Must not be called while holding [lock]: `computeIfAbsent` runs this mapping function under
     * the map's own bin lock, and the `get` inside it takes [lock] - so calling it from within a
     * `synchronized(lock)` block would establish the opposite lock order and can deadlock. Nothing
     * does that today; [invalidateFor] reaches the map directly for exactly this reason.
     */
    private fun stateFor(key: AppIconCache.Key): MutableStateFlow<ImageBitmap?> =
        states.computeIfAbsent(key) { MutableStateFlow(get(key)) }

    /**
     * Warms keys that nobody is watching yet, so the first frame that does watch them finds the
     * bitmap already cached.
     *
     * Passes a `null` target flow on purpose: a prefetched key has no watcher, and minting a
     * [MutableStateFlow] for it would make `states` grow with everything ever warmed rather than
     * with what is actually on screen.
     */
    override fun prefetch(keys: List<AppIconCache.Key>) {
        keys.forEach { request(it, state = null) }
    }

    /**
     * Starts a resolution unless the icon is cached or one is already running.
     *
     * Registration and the generation capture both happen under [lock], before the coroutine is
     * dispatched. Registering afterwards left a window in which an invalidation saw the key in
     * neither the cache nor [inFlight], so it neither cancelled nor re-requested it; the resolution
     * then failed its generation check, nothing replaced it, and the icon never appeared.
     *
     * [state] is the flow to publish into, or `null` when nobody is watching yet (see [prefetch]).
     * The cache write does not depend on it.
     */
    private fun request(
        key: AppIconCache.Key,
        state: MutableStateFlow<ImageBitmap?>? = stateFor(key),
    ) {
        synchronized(lock) {
            if (cache[key] != null) return
            if (inFlight.containsKey(key)) return
            val generation = generations[key.packageName] ?: 0L
            inFlight[key] =
                scope.launch {
                    // Read before the critical section: currentCoroutineContext() suspends, and the
                    // `finally` below must not suspend while holding the lock.
                    val self = currentCoroutineContext()[Job]
                    var resolved: ImageBitmap? = null
                    try {
                        resolved = resolve(key)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Exception, not Throwable: an OutOfMemoryError or LinkageError here is not
                        // a failed icon lookup, and swallowing it as a warning hides a real fault.
                        Log.w(TAG, "failed to resolve icon for ${key.packageName}", e)
                    } finally {
                        // Cleanup shares the critical section with registration, so it cannot evict
                        // a replacement that an invalidation installed in the meantime - which a
                        // bare `inFlight.remove(key)` could, since it never checks whose marker it
                        // is removing. The cache write and the state publish are gated the same way:
                        // publishing outside this section let an invalidation's null (or a
                        // replacement result) be overwritten by a superseded bitmap.
                        val bitmap = resolved
                        synchronized(lock) {
                            if (inFlight[key] === self) {
                                inFlight.remove(key)
                                if (bitmap != null && (generations[key.packageName] ?: 0L) == generation) {
                                    cache[key] = bitmap
                                    state?.value = bitmap
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun resolve(key: AppIconCache.Key): ImageBitmap? {
        val packageManager = context.packageManager
        return resolveInstalledIcon(packageManager, key.packageName)
            ?: key.apkFilePath?.let { resolveApkIcon(packageManager, it) }
    }

    private fun resolveInstalledIcon(packageManager: PackageManager, packageName: String): ImageBitmap? =
        try {
            packageManager
                .getApplicationIcon(packageName)
                .toTargetSize()
                ?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "failed to load installed icon for $packageName", e)
            null
        }

    private fun resolveApkIcon(packageManager: PackageManager, apkFilePath: String): ImageBitmap? =
        try {
            val info = getPackageArchiveInfoCompat(packageManager, apkFilePath)
            val appInfo = info?.applicationInfo ?: return null
            appInfo.sourceDir = apkFilePath
            appInfo.publicSourceDir = apkFilePath
            appInfo
                .loadIcon(packageManager)
                ?.toTargetSize()
                ?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "failed to load icon from APK at $apkFilePath", e)
            null
        }

    /**
     * Rasterises a drawable at the largest size any caller draws icons at.
     *
     * Without a target size this keeps the drawable's intrinsic bitmap, which for an adaptive icon
     * is the full 108dp canvas - roughly 432x432 on this device, while the biggest consumer draws at
     * 64dp. Caching the full-size bitmap multiplies both the resident memory and the decode time by
     * about ten for no visible benefit, since everything is downscaled on draw anyway. One shared
     * target size also means the cache holds a single entry per package instead of one per call
     * site, which is why the size is not part of [AppIconCache.Key].
     */
    private fun Drawable.toTargetSize(): Bitmap? {
        // A drawable without an intrinsic size can still be drawn into explicit bounds, and shape
        // or colour based icons legitimately report -1. Giving up here would drop an icon that is
        // perfectly renderable, so fall back to the target box instead. Always pass explicit
        // dimensions to toBitmap: the no-argument overload re-reads the intrinsic size and would
        // throw IllegalArgumentException for a negative one.
        val safeWidth = intrinsicWidth.takeIf { it > 0 } ?: targetIconPx
        val safeHeight = intrinsicHeight.takeIf { it > 0 } ?: targetIconPx
        val scale = targetIconPx.toFloat() / maxOf(safeWidth, safeHeight)
        if (scale >= 1f) return toBitmap(safeWidth, safeHeight)
        return toBitmap(
            width = (safeWidth * scale).roundToInt().coerceAtLeast(1),
            height = (safeHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun getPackageArchiveInfoCompat(packageManager: PackageManager, apkFilePath: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFilePath,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkFilePath, 0)
        }

    /**
     * Drops cached icons for packages that are replaced, removed or newly installed.
     *
     * Without this the cache would keep serving the icon a package had before it was updated, for
     * as long as the process lives.
     */
    private fun registerPackageChangeListener() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent != null) invalidateFor(intent)
                }
            }
        // Same shape as TimeZoneChangeSignal: the flag is required from TIRAMISU on, and every
        // action here is a protected system broadcast, so not exporting is also the accurate
        // description of who may reach this receiver.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Drops what is known about a package that just changed and starts over.
     *
     * The affected set spans all three places a key can be: cached, mid-resolve, or held only by a
     * watcher. Each omission has its own failure mode:
     *
     * - from the cache alone, a key that is mid-resolve keeps its marker, [request] skips it as
     *   "already resolving", its result is rejected by the generation check when it lands, and the
     *   icon never appears at all;
     * - stopping at the cache and in-flight misses a key whose entry the LRU has already evicted
     *   while a composable still watches its flow. `states` outlives the cache, so that flow keeps
     *   the pre-update bitmap, is never reset, and the tile shows the old icon for as long as it
     *   stays composed - contradicting the class doc's promise that an updated package stops
     *   showing its previous icon.
     *
     * Bumping the generation inside the same critical section is what makes the orderings safe:
     * a resolution that registered before this point carries the old generation and is rejected
     * when it lands; one that registers after captures the new one.
     */
    private fun invalidateFor(intent: Intent) {
        val changed = intent.data?.schemeSpecificPart ?: return
        val affected: List<AppIconCache.Key>
        val cancelled: List<Job>
        synchronized(lock) {
            generations[changed] = (generations[changed] ?: 0L) + 1
            affected =
                (cache.keys + inFlight.keys + states.keys)
                    .filter { it.packageName == changed }
            affected.forEach { cache.remove(it) }
            cancelled = affected.mapNotNull { inFlight.remove(it) }
            // Reset the per-key flows inside the critical section, for the same reason `publish`
            // writes the state inside it. Done after the lock is released, this null can land on
            // top of a replacement resolution that a concurrent observer already started under the
            // new generation: the fresh bitmap reaches the cache, the watcher is then handed null,
            // and the re-request below returns early because the cache is populated again - so the
            // key is cached but every watcher shows the placeholder until the process dies.
            // Indexing the map rather than calling stateFor(): the latter goes through
            // computeIfAbsent, which would mint an entry for a key that is being torn down here.
            // `lock` itself is reentrant, so re-entering it would not be the problem.
            affected.forEach { states[it]?.value = null }
        }
        cancelled.forEach { it.cancel() }
        affected.forEach { request(it) }
    }
}

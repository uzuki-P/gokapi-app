@file:Suppress("FunctionName")

package com.uzukip.gokapi.desktop.platform

import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// Adapted from metrolist-desktop's `platform/linux/LinuxSniTray.kt` (itself from lyrics-float
// and pray-time): the StatusNotifierItem and com.canonical.dbusmenu interfaces, the
// Tuple-returning GetLayout, the AboutToShow revision bump, LayoutUpdated on state changes,
// and re-registration when the watcher restarts are unchanged. The menu is Gokapi's.

class IconPixmap(
    @field:Position(0) val width: Int,
    @field:Position(1) val height: Int,
    @field:Position(2) val pixels: ByteArray,
) : Struct()

class ToolTip(
    @field:Position(0) val iconName: String,
    @field:Position(1) val iconPixmap: List<IconPixmap>,
    @field:Position(2) val title: String,
    @field:Position(3) val description: String,
) : Struct()

class MenuLayout(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<Any?>>,
    @field:Position(2) val children: List<Variant<MenuLayout>>,
) : Struct()

// Tuple, not Struct: GetLayout must return two separate out-args (u + (ia{sv}av)),
// which is what Qt's DBusMenuImporter demarshals. A single Struct produces one
// nested out-arg and Qt silently fails to read the layout, leaving the menu empty.
class MenuLayoutResult<A, B>(
    @field:Position(0) val revision: A,
    @field:Position(1) val layout: B,
) : Tuple()

class ItemProperties(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<Any?>>,
) : Struct()

class EventData(
    @field:Position(0) val id: Int,
    @field:Position(1) val eventId: String,
    @field:Position(2) val data: Variant<Any?>,
    @field:Position(3) val timestamp: UInt32,
) : Struct()

class IdAboutToShow(
    @field:Position(0) val id: Int,
    @field:Position(1) val needUpdate: Boolean,
) : Struct()

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface SniItem : DBusInterface {
    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getCategory(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getId(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getTitle(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getStatus(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getWindowId(): Int

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getItemIsMenu(): Boolean

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getMenu(): DBusPath

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getIconName(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getIconPixmap(): List<IconPixmap>

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getAttentionIconName(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getAttentionIconPixmap(): List<IconPixmap>

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getOverlayIconName(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getOverlayIconPixmap(): List<IconPixmap>

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getToolTip(): ToolTip

    fun Activate(
        x: Int,
        y: Int,
    )

    fun SecondaryActivate(
        x: Int,
        y: Int,
    )

    fun Scroll(
        delta: Int,
        orientation: String,
    )

    fun ContextMenu(
        x: Int,
        y: Int,
    )
}

@DBusInterfaceName("com.canonical.dbusmenu")
interface SniMenu : DBusInterface {
    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getVersion(): UInt32

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getStatus(): String

    @DBusBoundProperty(access = DBusPropertyAccess.READ)
    fun getIconThemePath(): List<String>

    fun GetLayout(
        parentId: Int,
        recursionDepth: Int,
        propertyNames: List<String>,
    ): MenuLayoutResult<UInt32, MenuLayout>

    fun GetGroupProperties(
        ids: List<Int>,
        propertyNames: List<String>,
    ): List<ItemProperties>

    fun AboutToShow(id: Int): Boolean

    fun Event(
        id: Int,
        eventId: String,
        data: Variant<Any?>,
        timestamp: UInt32,
    )

    fun EventGroup(events: List<EventData>): List<Int>

    fun AboutToShowGroup(ids: List<Int>): List<IdAboutToShow>

    /** Tells the host to refetch the layout, so labels follow state while the menu is open. */
    class LayoutUpdated(
        path: String,
        val revision: UInt32,
        val parent: Int,
    ) : DBusSignal(path, revision, parent)
}

/** Desktop notifications (the freedesktop.org spec), sent over the tray's bus connection. */
@DBusInterfaceName("org.freedesktop.Notifications")
interface Notifications : DBusInterface {
    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32

    class ActionInvoked(path: String, val id: UInt32, val actionKey: String) : DBusSignal(path, id, actionKey)
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcherRemote : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

private typealias DBusPropertyAccess = org.freedesktop.dbus.annotations.DBusProperty.Access

private const val ITEM_PATH = "/StatusNotifierItem"
private const val MENU_PATH = "/StatusNotifierMenu"
private const val WATCHER_NAME = "org.kde.StatusNotifierWatcher"
private const val WATCHER_PATH = "/StatusNotifierWatcher"
private const val NOTIFICATIONS_NAME = "org.freedesktop.Notifications"
private const val NOTIFICATIONS_PATH = "/org/freedesktop/Notifications"

// Menu ids
private const val MENU_HEADER = 1
private const val MENU_WINDOW = 2
private const val MENU_UPLOAD = 3
private const val MENU_QUIT = 4
private const val SEPARATOR_1 = 10
private const val SEPARATOR_2 = 11

/** StatusNotifierItem tray for Plasma and other Freedesktop shells over the session bus. */
class LinuxSniTray(
    private val model: AppTrayModel,
    private val scope: CoroutineScope,
) : AppTray {
    @Volatile
    private var connection: DBusConnection? = null
    private val revision = AtomicInteger(1)
    private val availableState = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = availableState.asStateFlow()
    private var stateJob: Job? = null
    private val sentNotifications = java.util.Collections.synchronizedSet(HashSet<Long>())
    private val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"

    private val pixmaps: List<IconPixmap> by lazy {
        SIZES.mapNotNull { size ->
            TrayIconImages.render(size)?.let { IconPixmap(it.width, it.height, TrayIconImages.argbNetworkOrder(it)) }
        }
    }

    override fun start() {
        if (connection != null) return
        thread(name = "gokapi-sni", isDaemon = true) {
            try {
                val conn = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                connection = conn
                conn.requestBusName(busName)
                conn.exportObject(MENU_PATH, MenuImpl())
                conn.exportObject(ITEM_PATH, ItemImpl())
                // Plasma restarts (or a late kded6) bring a new watcher that does not know us.
                conn.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
                    if (signal.name != WATCHER_NAME) return@addSigHandler
                    if (signal.newOwner.isEmpty()) availableState.value = false
                    else thread(name = "gokapi-sni-register", isDaemon = true) { register(conn) }
                }
                // Clicking one of our notifications shows the window.
                conn.addSigHandler(Notifications.ActionInvoked::class.java) { signal ->
                    if (signal.actionKey == "default" && sentNotifications.remove(signal.id.toLong())) model.onNotificationClicked()
                }
                register(conn)
                // Keep the Show/Hide label current while the menu is open.
                stateJob = scope.launch {
                    model.windowVisible.drop(1).collect {
                        val rev = revision.incrementAndGet()
                        connection?.let { runCatching { it.sendMessage(SniMenu.LayoutUpdated(MENU_PATH, UInt32(rev.toLong()), 0)) } }
                    }
                }
            } catch (t: Throwable) {
                System.err.println("Tray unavailable: ${t.message}")
                close()
            }
        }
    }

    private fun register(conn: DBusConnection) {
        availableState.value = try {
            conn.getRemoteObject(WATCHER_NAME, WATCHER_PATH, StatusNotifierWatcherRemote::class.java)
                .RegisterStatusNotifierItem(busName)
            true
        } catch (t: Throwable) {
            // No tray host (no Plasma panel, no watcher): close-to-tray must not hide the window.
            false
        }
    }

    override fun notify(title: String, body: String) {
        val conn = connection ?: return
        thread(name = "gokapi-notify", isDaemon = true) {
            runCatching {
                val id = conn.getRemoteObject(NOTIFICATIONS_NAME, NOTIFICATIONS_PATH, Notifications::class.java).Notify(
                    model.title, UInt32(0), iconPath(), title, body,
                    listOf("default", "Open"),
                    // desktop-entry lets Plasma group the notification under the installed launcher.
                    mapOf("desktop-entry" to Variant(AppDirs.APP_ID)),
                    -1,
                )
                sentNotifications += id.toLong()
            }
        }
    }

    // Notification servers read the icon from a file path.
    private fun iconPath(): String = runCatching {
        val file = java.io.File(System.getProperty("java.io.tmpdir"), "${AppDirs.APP_ID}-${ProcessHandle.current().pid()}.png")
        if (!file.isFile) {
            TrayIconImages.render(128)?.let { javax.imageio.ImageIO.write(it, "png", file) }
            file.deleteOnExit()
        }
        file.absolutePath
    }.getOrDefault("")

    override fun close() {
        stateJob?.cancel()
        stateJob = null
        availableState.value = false
        connection?.let { conn ->
            runCatching { conn.disconnect() }
            connection = null
        }
    }

    private fun menuItems(): List<MenuLayout> = listOf(
        MenuLayout(MENU_HEADER, propsFor(MENU_HEADER), emptyList()),
        MenuLayout(SEPARATOR_1, separatorProps(), emptyList()),
        MenuLayout(MENU_WINDOW, propsFor(MENU_WINDOW), emptyList()),
        MenuLayout(MENU_UPLOAD, propsFor(MENU_UPLOAD), emptyList()),
        MenuLayout(SEPARATOR_2, separatorProps(), emptyList()),
        MenuLayout(MENU_QUIT, propsFor(MENU_QUIT), emptyList()),
    )

    private fun propsFor(id: Int): Map<String, Variant<Any?>> = when (id) {
        MENU_HEADER -> itemProps(model.title, enabled = false)
        MENU_WINDOW -> if (model.windowVisible.value) itemProps("Hide window", "window-minimize") else itemProps("Show window", "window-restore")
        MENU_UPLOAD -> itemProps("Upload files…", "cloud-upload")
        MENU_QUIT -> itemProps("Quit", "application-exit")
        SEPARATOR_1, SEPARATOR_2 -> separatorProps()
        else -> emptyMap()
    }

    private fun separatorProps(): Map<String, Variant<Any?>> = mapOf("type" to Variant("separator"))

    // Theme icon names, so the panel draws the menu icons in its own style.
    private fun itemProps(label: String, iconName: String? = null, enabled: Boolean = true): Map<String, Variant<Any?>> =
        linkedMapOf<String, Variant<Any?>>("label" to Variant(label), "enabled" to Variant(enabled))
            .apply { if (iconName != null) put("icon-name", Variant(iconName)) }

    private fun rootLayout(): MenuLayout =
        MenuLayout(0, mapOf("children-display" to Variant("submenu")), menuItems().map { Variant(it) })

    private inner class ItemImpl : SniItem {
        override fun getCategory() = "ApplicationStatus"
        override fun getId() = AppDirs.APP_ID
        override fun getTitle() = model.title
        override fun getStatus() = "Active"
        override fun getWindowId() = 0
        override fun getItemIsMenu() = false

        // Hand the menu to Plasma: right-click is rendered by plasmashell over DBusMenu.
        override fun getMenu() = DBusPath(MENU_PATH)
        override fun getIconName() = ""
        override fun getIconPixmap() = pixmaps
        override fun getAttentionIconName() = ""
        override fun getAttentionIconPixmap() = emptyList<IconPixmap>()
        override fun getOverlayIconName() = ""
        override fun getOverlayIconPixmap() = emptyList<IconPixmap>()
        override fun getToolTip() = ToolTip("", emptyList(), model.title, "Private file sharing")

        // Left click shows or hides the window.
        override fun Activate(x: Int, y: Int) = model.onToggleWindow()
        override fun SecondaryActivate(x: Int, y: Int) = model.onToggleWindow()
        override fun Scroll(delta: Int, orientation: String) {}
        override fun ContextMenu(x: Int, y: Int) {}
        override fun getObjectPath(): String = ITEM_PATH
        override fun toString(): String = "GokapiSniItem"
    }

    private inner class MenuImpl : SniMenu {
        override fun getVersion() = UInt32(3L)
        override fun getStatus() = "normal"
        override fun getIconThemePath() = emptyList<String>()

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): MenuLayoutResult<UInt32, MenuLayout> {
            val layout = if (parentId == 0) rootLayout() else menuItems().firstOrNull { it.id == parentId } ?: rootLayout()
            return MenuLayoutResult(UInt32(revision.get().toLong()), layout)
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<ItemProperties> =
            ids.map { ItemProperties(it, propsFor(it)) }

        // Root always reports changes on open so Plasma refetches the layout; the revision
        // must move or Qt's importer silently drops the update, leaving labels stale.
        override fun AboutToShow(id: Int): Boolean {
            if (id == 0) {
                revision.incrementAndGet()
                return true
            }
            return false
        }

        override fun Event(id: Int, eventId: String, data: Variant<Any?>, timestamp: UInt32) {
            if (eventId != "clicked") return
            when (id) {
                MENU_WINDOW -> model.onToggleWindow()
                MENU_UPLOAD -> model.onUpload()
                MENU_QUIT -> model.onQuit()
            }
        }

        override fun EventGroup(events: List<EventData>): List<Int> {
            events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
            return emptyList()
        }

        override fun AboutToShowGroup(ids: List<Int>) = ids.map { IdAboutToShow(it, false) }
        override fun getObjectPath(): String = MENU_PATH
        override fun toString(): String = "GokapiSniMenu"
    }

    private companion object {
        /** 16 to 32 px cover normal panels; 48 to 128 px cover HiDPI scaling. */
        val SIZES = listOf(16, 22, 24, 32, 48, 64, 128)
    }
}

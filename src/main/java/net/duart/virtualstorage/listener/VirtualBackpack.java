package net.duart.virtualstorage.listener;

import net.duart.virtualstorage.util.FileHandlers;
import net.duart.virtualstorage.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class VirtualBackpack implements Listener {

    private final Plugin plugin;

    private final ConcurrentHashMap<UUID, Integer> currentPageIndexMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ArrayList<Inventory>> backpacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> adminViewTargets = new ConcurrentHashMap<>();

    private final Set<UUID> playersWithOpenBackpack = ConcurrentHashMap.newKeySet();
    private final Set<UUID> editableAdminViewers = ConcurrentHashMap.newKeySet();
    private final Set<Inventory> backpackInventories = new HashSet<>();

    private final FileHandlers fileHandlers;
    private final NamespacedKey NAV_KEY;

    private static final int NAV_PREV_SLOT = 45;
    private static final int NAV_NEXT_SLOT = 53;
    private static final int INVENTORY_SIZE = 54;

    public VirtualBackpack(Plugin plugin, FileHandlers fileHandlers) {
        this.plugin = plugin;
        this.fileHandlers = fileHandlers;
        NAV_KEY = new NamespacedKey(plugin, "navarrow");
    }

    /* OPEN BACKPACK HANDLERS */
    public void openBackpack(Player player) {
        UUID playerId = player.getUniqueId();

        if (playersWithOpenBackpack.contains(playerId)) {
            player.sendMessage(Messages.get("waitToOpen"));
            return;
        }

        currentPageIndexMap.put(playerId, 0);
        markBackpackOpen(player);

        ArrayList<Inventory> loadedPages = backpacks.get(playerId);
        if (loadedPages != null && !loadedPages.isEmpty()) {
            refreshPagesAndNavigation(loadedPages);
            player.openInventory(loadedPages.get(0));
            return;
        }

        File gzippedFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml.gz");
        File yamlFile = new File(plugin.getDataFolder(), player.getName() + " - " + playerId + ".yml");

        CompletableFuture.supplyAsync(() -> {
            FileHandlers.BackpackData data = null;

            try {
                if (gzippedFile.exists()) {
                    data = fileHandlers.loadBackpackData(playerId, gzippedFile);
                } else if (yamlFile.exists() || !gzippedFile.exists()) {
                    data = fileHandlers.loadBackpackData(playerId, yamlFile);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading backpack for player " + player.getName(), e);
            }

            return data;

        }).thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
            ArrayList<Inventory> pages = backpacks.computeIfAbsent(playerId, k -> new ArrayList<>());

            pages.clear();

            if (data != null && data.pageCount() > 0) {
                for (int i = 0; i < data.pageCount(); i++) {
                    Inventory page = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(i + 1, data.pageCount()));

                    Map<Integer, ItemStack> pageItems = data.pages().get(i);
                    if (pageItems != null) {
                        for (Map.Entry<Integer, ItemStack> entry : pageItems.entrySet()) {
                            int slot = entry.getKey();
                            if (slot >= 0 && slot < page.getSize()) {
                                page.setItem(slot, entry.getValue());
                            }
                        }
                    }

                    pages.add(page);
                    registerBackpackInventory(page);
                }
            }
            rebuildPageTitles(pages);
            ensurePageCountMatchesPermissions(playerId, pages, false);
            refreshPagesAndNavigation(pages);

            if (!pages.isEmpty()) {
                player.openInventory(pages.get(0));
            }
        }));
    }

    public void openTargetBackpack(Player admin, Player target, boolean editMode) {
        boolean hasPermission = false;

        for (int i = 999; i >= 1; i--) {
            if (target.hasPermission("virtualstorages.use." + i)) {
                hasPermission = true;
                break;
            }
        }

        if (!hasPermission) {
            admin.sendMessage(Messages.get("noPermissionOther"));
            return;
        }

        UUID targetId = target.getUniqueId();

        markAdminViewing(admin, targetId, editMode);
        currentPageIndexMap.put(admin.getUniqueId(), 0);

        ArrayList<Inventory> loadedPages = backpacks.get(targetId);
        if (loadedPages != null && !loadedPages.isEmpty()) {
            refreshPagesAndNavigation(loadedPages);
            admin.openInventory(loadedPages.get(0));
            return;
        }

        File gzippedFile = new File(plugin.getDataFolder(), target.getName() + " - " + targetId + ".yml.gz");
        File yamlFile = new File(plugin.getDataFolder(), target.getName() + " - " + targetId + ".yml");

        CompletableFuture.supplyAsync(() -> {
            FileHandlers.BackpackData data = null;

            try {
                if (gzippedFile.exists()) {
                    data = fileHandlers.loadBackpackData(targetId, gzippedFile);
                } else if (yamlFile.exists()) {
                    data = fileHandlers.loadBackpackData(targetId, yamlFile);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error handling backpack files for player " + target.getName(), e);
            }

            return data;

        }).thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
            ArrayList<Inventory> pages = backpacks.computeIfAbsent(targetId, k -> new ArrayList<>());

            pages.clear();

            if (data != null && data.pageCount() > 0) {
                for (int i = 0; i < data.pageCount(); i++) {
                    Inventory page = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(i + 1, data.pageCount()));

                    Map<Integer, ItemStack> pageItems = data.pages().get(i);
                    if (pageItems != null) {
                        for (Map.Entry<Integer, ItemStack> entry : pageItems.entrySet()) {
                            int slot = entry.getKey();
                            if (slot >= 0 && slot < page.getSize()) {
                                page.setItem(slot, entry.getValue());
                            }
                        }
                    }

                    pages.add(page);
                    registerBackpackInventory(page);
                }
            }

            rebuildPageTitles(pages);
            ensurePageCountMatchesPermissions(targetId, pages, true);
            refreshPagesAndNavigation(pages);

            if (!pages.isEmpty()) {
                admin.openInventory(pages.get(0));
            }
        }));
    }

    /* EVENTS */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        if (!isBackpackInventory(topInventory)) {
            return;
        }

        UUID targetId = getViewedTargetId(player);
        boolean adminViewer = isAdminViewing(playerId);
        boolean editableAdminViewer = isEditableAdminViewer(playerId);
        boolean clickedTopInventory = clickedInventory != null && clickedInventory.equals(topInventory);

        if (adminViewer && !clickedTopInventory && shouldCancelAdminBackpackEdit(editableAdminViewer, true)) {
            event.setCancelled(true);
            return;
        }

        if (clickedInventory == null) {
            return;
        }

        if (!clickedTopInventory) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        ArrayList<Inventory> pages = getBackpackPages(targetId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(playerId, 0);
        if (pages.isEmpty()) {
            return;
        }
        if (currentPageIndex >= pages.size()) {
            currentPageIndex = 0;
            currentPageIndexMap.put(playerId, currentPageIndex);
        }
        Inventory currentPage = pages.get(currentPageIndex);

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();
        boolean navigationSlot = isNavigationSlot(slot);
        boolean navigationItem = isNavigationItem(clickedItem);
        boolean currentPageClick = clickedInventory.equals(currentPage);

        NavigationClickDecision decision = decideNavigationClick(
                navigationSlot,
                navigationItem,
                currentPageClick,
                event.getClick().isLeftClick(),
                event.isShiftClick(),
                event.getClick().isKeyboardClick()
        );
        if (decision.cancelClick()) {
            event.setCancelled(true);
        }

        if (adminViewer && !navigationItem && shouldCancelAdminBackpackEdit(editableAdminViewer, true)) {
            event.setCancelled(true);
            return;
        }

        if (!currentPageClick) {
            return;
        }

        if (!navigationSlot || !navigationItem) {
            return;
        }

        if (!decision.changePage()) {
            return;
        }

        int direction = slot == NAV_PREV_SLOT ? -1 : 1;
        changePage(targetId, direction, player);

        int updatedPageIndex = currentPageIndexMap.getOrDefault(playerId, 0);
        Inventory updatedPage = getBackpackPages(targetId).get(updatedPageIndex);
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(updatedPage));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!isBackpackInventory(topInventory)) {
            return;
        }

        UUID viewerId = event.getWhoClicked().getUniqueId();
        if (isAdminViewing(viewerId) &&
                shouldCancelAdminBackpackEdit(isEditableAdminViewer(viewerId), true) &&
                dragTouchesTopInventory(event.getRawSlots(), topInventory.getSize())) {
            event.setCancelled(true);
            return;
        }

        if (dragTouchesNavigationSlot(event.getRawSlots(), topInventory.getSize())) {
            event.setCancelled(true);
        }
    }

    record NavigationClickDecision(boolean cancelClick, boolean changePage) { }

    static NavigationClickDecision decideNavigationClick(
            boolean navigationSlot,
            boolean navigationItem,
            boolean currentPageClick,
            boolean leftClick,
            boolean shiftClick,
            boolean keyboardClick
    ) {
        boolean cancelClick = navigationSlot || navigationItem;
        boolean changePage = currentPageClick && navigationSlot && navigationItem && (leftClick || shiftClick);
        return new NavigationClickDecision(cancelClick, changePage);
    }

    static boolean dragTouchesNavigationSlot(Set<Integer> rawSlots, int topInventorySize) {
        for (int rawSlot : rawSlots) {
            if (rawSlot >= 0 && rawSlot < topInventorySize && isNavigationSlot(rawSlot)) {
                return true;
            }
        }

        return false;
    }

    static boolean dragTouchesTopInventory(Set<Integer> rawSlots, int topInventorySize) {
        for (int rawSlot : rawSlots) {
            if (rawSlot >= 0 && rawSlot < topInventorySize) {
                return true;
            }
        }

        return false;
    }

    static boolean shouldCancelAdminBackpackEdit(boolean editMode, boolean backpackViewOpen) {
        return backpackViewOpen && !editMode;
    }

    private static boolean isNavigationSlot(int slot) {
        return slot == NAV_PREV_SLOT || slot == NAV_NEXT_SLOT;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        Inventory closedInventory = event.getInventory();

        if (!isBackpackInventory(closedInventory)) {
            return;
        }

        UUID targetId = getViewedTargetId(player);

        ArrayList<Inventory> pages = getBackpackPages(targetId);
        int currentPageIndex = currentPageIndexMap.getOrDefault(playerId, 0);

        if (currentPageIndex >= pages.size() || !closedInventory.equals(pages.get(currentPageIndex))) {
            return;
        }

        pages.set(currentPageIndex, closedInventory);
        markBackpackClosed(player);

        if (!hasOtherViewers(pages, playerId)) {
            saveBackpackPages(targetId, pages, true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!hasOpenViewers(targetId)) {
                    unloadBackpack(targetId);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        UUID targetId = getViewedTargetId(player);
        boolean adminViewer = isAdminViewing(playerId);

        markBackpackClosed(player);

        if (adminViewer) {
            ArrayList<Inventory> targetPages = backpacks.get(targetId);
            if (targetPages != null && !hasOpenViewers(targetId)) {
                saveBackpackPages(targetId, targetPages, true);
                unloadBackpack(targetId);
            }
        }

        if (backpacks.containsKey(playerId) && !hasOpenViewers(playerId)) {
            ArrayList<Inventory> pages = backpacks.get(playerId);
            saveBackpackPages(playerId, pages, true);
            unloadBackpack(playerId);
        }
    }

    /* PERMISSION & STATE MANAGEMENT */
    private void ensurePageCountMatchesPermissions(UUID playerId, ArrayList<Inventory> pages, boolean isAdmin) {
        int allowedPages = getMaxPages(playerId);
        int currentPages = pages.size();

        if (currentPages > allowedPages) {
            List<ItemStack> overflowItems = new ArrayList<>();

            for (int pageIndex = currentPages - 1; pageIndex >= allowedPages; pageIndex--) {
                if (pageIndex >= pages.size()) continue;

                Inventory pageToRemove = pages.get(pageIndex);

                for (ItemStack item : pageToRemove.getContents()) {
                    if (item != null) {
                        isNavigationItem(item);
                    }
                }

                for (ItemStack item : pageToRemove.getContents()) {
                    if (item != null && !isNavigationItem(item)) {
                        boolean placed = false;

                        for (int targetPageIndex = 0; targetPageIndex < allowedPages && targetPageIndex < pages.size(); targetPageIndex++) {
                            Inventory targetPage = pages.get(targetPageIndex);
                            int freeSlot = findFirstFreeNonNavSlot(targetPage, false);
                            if (freeSlot != -1) {
                                targetPage.setItem(freeSlot, item.clone());
                                placed = true;
                                break;
                            }
                        }

                        if (!placed) {
                            overflowItems.add(item.clone());
                        }
                    }
                }
            }

            while (pages.size() > allowedPages) {
                Inventory removedPage = pages.remove(pages.size() - 1);
                unregisterBackpackInventory(removedPage);
            }

            if (!overflowItems.isEmpty()) {
                fileHandlers.saveOverflowItems(playerId, overflowItems);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Messages.get("itemsOverflowed"));
                }
            } else {
                try {
                    Files.deleteIfExists(Paths.get(plugin.getDataFolder().getPath(), playerId + "-overflow-.yml.gz"));
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to delete overflow file for player: " + playerId);
                }
            }

            refreshPagesAndNavigation(pages);
            return;
        }

        if (currentPages < allowedPages) {
            for (int i = currentPages; i < allowedPages; i++) {
                Inventory page = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(i + 1, allowedPages));
                pages.add(page);
                registerBackpackInventory(page);
            }

            List<ItemStack> overflowItems = fileHandlers.loadOverflowItems(playerId);
            if (!overflowItems.isEmpty()) {
                for (ItemStack item : new ArrayList<>(overflowItems)) {
                    boolean placed = false;
                    for (Inventory page : pages) {
                        int slot = findFirstFreeNonNavSlot(page, false);
                        if (slot == NAV_NEXT_SLOT) continue;
                        if (slot != -1) {
                            page.setItem(slot, item);
                            overflowItems.remove(item);
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) break;
                }

                if (overflowItems.isEmpty()) {
                    try {
                        Files.deleteIfExists(Paths.get(plugin.getDataFolder().getPath(), playerId + "-overflow-.yml.gz"));
                    } catch (IOException e) {
                        plugin.getLogger().warning("Failed to delete overflow file for player: " + playerId);
                    }
                } else {
                    fileHandlers.saveOverflowItems(playerId, overflowItems);
                }

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Messages.get("itemsRecovered"));
                }
            }
        }

        if (isAdmin) {
            List<ItemStack> overflowItems = fileHandlers.loadOverflowItems(playerId);
            while (!overflowItems.isEmpty()) {
                Inventory overflowPage = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(pages.size() + 1, "OVERFLOW"));
                registerBackpackInventory(overflowPage);

                for (ItemStack item : new ArrayList<>(overflowItems)) {
                    int slot = findFirstFreeNonNavSlot(overflowPage, false);
                    if (slot == NAV_NEXT_SLOT) continue;
                    if (slot != -1) {
                        overflowPage.setItem(slot, item);
                        overflowItems.remove(item);
                    } else break;
                }

                pages.add(overflowPage);
            }
        }

        for (int i = 0; i < pages.size(); i++) {
            Inventory oldPage = pages.get(i);
            Inventory newPage = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(i + 1, pages.size()));
            newPage.setContents(oldPage.getContents());
            pages.set(i, newPage);
            registerBackpackInventory(newPage);
        }

        addNavigationItems(pages);
    }

    private int getMaxPages(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            for (int i = 999; i >= 1; i--) {
                if (player.hasPermission("virtualstorages.use." + i)) {
                    return i;
                }
            }
        }
        return 1;
    }

    /* INVENTORY MANAGEMENT */

    private ArrayList<Inventory> getBackpackPages(UUID playerId) {
        int maxPages = getMaxPages(playerId);
        return backpacks.computeIfAbsent(playerId, k -> createNewBackpackPages(maxPages));
    }

    private ArrayList<Inventory> createNewBackpackPages(int maxPages) {
        ArrayList<Inventory> pages = new ArrayList<>();
        for (int i = 0; i < maxPages; i++) {
            Inventory page = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(i + 1, maxPages));
            pages.add(page);
            registerBackpackInventory(page);
        }
        addNavigationItems(pages);
        return pages;
    }

    private int findFirstFreeNonNavSlot(Inventory inv, boolean allowSlot53IfOccupied) {
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == NAV_PREV_SLOT) continue;

            ItemStack it = inv.getItem(slot);

            if (slot == NAV_NEXT_SLOT) {
                if (isNavigationItem(it)) continue;

                if (it == null) return slot;

                if (allowSlot53IfOccupied) return slot;
                continue;
            }

            if (it == null) return slot;
        }
        return -1;
    }

    private void refreshPagesAndNavigation(ArrayList<Inventory> pages) {
        int totalPages = pages.size();
        UUID playerId = getPlayerIdByInventory(pages);

        for (int i = 0; i < totalPages; i++) {
            Inventory page = pages.get(i);

            if (i < totalPages - 1) {
                handleSlotItem(pages, i, totalPages, playerId);
            }

            if (totalPages == 1) {
                for (int navSlot : new int[]{NAV_PREV_SLOT, NAV_NEXT_SLOT}) {
                    ItemStack navItem = page.getItem(navSlot);
                    if (isNavigationItem(navItem)) page.setItem(navSlot, null);
                }
            } else {
                if (i > 0) page.setItem(NAV_PREV_SLOT, createNavigationItem(Messages.get("prevArrow")));
                if (i < totalPages - 1) page.setItem(NAV_NEXT_SLOT, createNavigationItem(Messages.get("nextArrow")));
            }
        }
    }

    private boolean isBackpackInventory(Inventory inventory) {
        return backpackInventories.contains(inventory);
    }

    /* ITEM MOVEMENT & OVERFLOW */

    private void handleSlotItem(List<Inventory> pages, int pageIndex, int totalPages, UUID playerId) {
        if (pageIndex >= totalPages - 1) {
            handleOverflowItem(playerId, pages.get(pageIndex).getItem(NAV_NEXT_SLOT));
            pages.get(pageIndex).setItem(NAV_NEXT_SLOT, null);
            return;
        }

        Inventory sourcePage = pages.get(pageIndex);
        ItemStack item = sourcePage.getItem(NAV_NEXT_SLOT);
        if (item == null || isNavigationItem(item)) return;

        boolean placed = tryPlaceItemInPages(pages, pageIndex + 1, totalPages, item);

        if (placed) {
            sourcePage.setItem(NAV_NEXT_SLOT, null);
        } else {
            handleOverflowItem(playerId, item);
            sourcePage.setItem(NAV_NEXT_SLOT, null);
        }
    }

    private void handleSlotItem(List<Inventory> pages, int pageIndex, int totalPages, UUID playerId, ItemStack item) {
        if (pageIndex >= totalPages - 1) {
            handleOverflowItem(playerId, item);
            return;
        }

        if (item == null || isNavigationItem(item)) return;

        boolean placed = tryPlaceItemInPages(pages, pageIndex + 1, totalPages, item);

        if (!placed) {
            handleOverflowItem(playerId, item);
        }
    }

    private boolean tryPlaceItemInPages(List<Inventory> pages, int startPage, int totalPages, ItemStack item) {
        int maxPagesToCheck = Math.min(totalPages, startPage + 10);

        for (int j = startPage; j < maxPagesToCheck; j++) {
            if (j >= pages.size()) {
                plugin.getLogger().warning("Page index " + j + " out of bounds during item placement");
                break;
            }

            int free = findFirstFreeNonNavSlot(pages.get(j), true);
            if (free != -1) {
                pages.get(j).setItem(free, item);
                return true;
            }
        }

        return false;
    }

    private void handleOverflowItem(UUID playerId, ItemStack item) {
        if (item == null || isNavigationItem(item)) return;

        if (playerId != null) {
            List<ItemStack> overflow = new ArrayList<>(fileHandlers.loadOverflowItems(playerId));
            overflow.add(item);
            fileHandlers.saveOverflowItems(playerId, overflow);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(Messages.get("itemsOverflowed"));
            }
        }
    }

    /* NAVIGATION & UI */

    private void addNavigationItems(List<Inventory> pages) {
        int totalPages = pages.size();
        UUID playerId = getPlayerIdByInventory(pages);

        for (int i = 0; i < totalPages; i++) {
            Inventory page = pages.get(i);

            ItemStack cur45 = page.getItem(NAV_PREV_SLOT);
            if (i > 0) {
                if (cur45 == null || isNavigationItem(cur45)) {
                    page.setItem(NAV_PREV_SLOT, createNavigationItem(Messages.get("prevArrow")));
                }
            } else {
                if (isNavigationItem(cur45)) page.setItem(NAV_PREV_SLOT, null);
            }

            ItemStack cur53 = page.getItem(NAV_NEXT_SLOT);
            if (i < totalPages - 1) {
                if (cur53 == null || isNavigationItem(cur53)) {
                    page.setItem(NAV_NEXT_SLOT, createNavigationItem(Messages.get("nextArrow")));
                } else {
                    ItemStack toMove = cur53.clone();
                    page.setItem(NAV_NEXT_SLOT, null);
                    handleSlotItem(pages, i, totalPages, playerId, toMove);
                    page.setItem(NAV_NEXT_SLOT, createNavigationItem(Messages.get("nextArrow")));
                }
            } else {
                if (isNavigationItem(cur53)) page.setItem(NAV_NEXT_SLOT, null);
            }
        }
    }

    private ItemStack createNavigationItem(String displayName) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.getPersistentDataContainer().set(NAV_KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void changePage(UUID targetId, int direction, Player viewer) {
        ArrayList<Inventory> pages = getBackpackPages(targetId);
        UUID viewerId = viewer.getUniqueId();
        int currentPageIndex = currentPageIndexMap.getOrDefault(viewerId, 0);
        int newPageIndex = currentPageIndex + direction;

        boolean viewerIsAdminViewingTarget = targetId.equals(adminViewTargets.get(viewerId));

        int allowedMax;
        if (viewerIsAdminViewingTarget) {
            allowedMax = pages.size();
        } else {
            allowedMax = getMaxPages(targetId);
            if (allowedMax <= 0) allowedMax = 1;
        }

        if (newPageIndex >= 0 && newPageIndex < allowedMax) {
            currentPageIndexMap.put(viewerId, newPageIndex);
        }
    }

    /* UTILITY & HELPERS */

    private String buildTitle(int page, Object maxPages) {
        return Messages.get("title", "%page%", String.valueOf(page), "%maxpages%", String.valueOf(maxPages));
    }

    private boolean isNavigationItem(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null &&
                meta.getPersistentDataContainer().has(NAV_KEY, PersistentDataType.BYTE);
    }

    private UUID getPlayerIdByInventory(List<Inventory> pages) {
        for (UUID id : backpacks.keySet()) {
            if (backpacks.get(id) == pages) return id;
        }
        return null;
    }

    private UUID getViewedTargetId(Player viewer) {
        return adminViewTargets.getOrDefault(viewer.getUniqueId(), viewer.getUniqueId());
    }

    private boolean isAdminViewing(UUID viewerId) {
        return adminViewTargets.containsKey(viewerId);
    }

    private boolean isEditableAdminViewer(UUID viewerId) {
        return editableAdminViewers.contains(viewerId);
    }

    private boolean hasOtherViewers(List<Inventory> pages, UUID closingViewerId) {
        for (Inventory inv : pages) {
            for (org.bukkit.entity.HumanEntity viewer : inv.getViewers()) {
                if (!viewer.getUniqueId().equals(closingViewerId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasOpenViewers(UUID targetId) {
        if (playersWithOpenBackpack.contains(targetId) || adminViewTargets.containsValue(targetId)) {
            return true;
        }

        ArrayList<Inventory> pages = backpacks.get(targetId);
        if (pages == null) {
            return false;
        }

        for (Inventory inv : pages) {
            if (!inv.getViewers().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void saveBackpackPages(UUID targetId, ArrayList<Inventory> pages, boolean splitOverflowPages) {
        if (!splitOverflowPages) {
            fileHandlers.saveBackpackInventoryForTarget(targetId, pages);
            return;
        }

        int maxPages = Math.max(1, getMaxPages(targetId));
        if (pages.size() <= maxPages) {
            fileHandlers.saveBackpackInventoryForTarget(targetId, pages);
            return;
        }

        List<ItemStack> overflowItems = new ArrayList<>();
        for (int i = maxPages; i < pages.size(); i++) {
            for (ItemStack item : pages.get(i).getContents()) {
                if (item != null && !isNavigationItem(item)) {
                    overflowItems.add(item.clone());
                }
            }
        }

        if (!overflowItems.isEmpty()) {
            fileHandlers.saveOverflowItems(targetId, overflowItems);
        } else {
            try {
                Files.deleteIfExists(Paths.get(plugin.getDataFolder().getPath(), targetId + "-overflow-.yml.gz"));
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete overflow file for player: " + targetId);
            }
        }

        ArrayList<Inventory> allowedPages = new ArrayList<>();
        for (int i = 0; i < maxPages && i < pages.size(); i++) {
            allowedPages.add(pages.get(i));
        }
        fileHandlers.saveBackpackInventoryForTarget(targetId, allowedPages);
    }

    private void rebuildPageTitles(List<Inventory> pages) {
        int totalPages = pages.size();
        for (int i = 0; i < totalPages; i++) {
            Inventory old = pages.get(i);
            Inventory rebuilt = Bukkit.createInventory(null, INVENTORY_SIZE, buildTitle(i + 1, totalPages));
            rebuilt.setContents(old.getContents());
            pages.set(i, rebuilt);
            registerBackpackInventory(rebuilt);
        }
    }

    /* MEMORY MANAGEMENT */

    private void markBackpackOpen(Player player) {
        playersWithOpenBackpack.add(player.getUniqueId());
    }

    private void markBackpackClosed(Player player) {
        UUID playerId = player.getUniqueId();
        playersWithOpenBackpack.remove(playerId);
        adminViewTargets.remove(playerId);
        editableAdminViewers.remove(playerId);
        currentPageIndexMap.remove(playerId);
    }

    private void markAdminViewing(Player admin, UUID targetId, boolean editMode) {
        UUID adminId = admin.getUniqueId();
        adminViewTargets.put(adminId, targetId);
        if (editMode) {
            editableAdminViewers.add(adminId);
        } else {
            editableAdminViewers.remove(adminId);
        }
    }

    private void registerBackpackInventory(Inventory inventory) {
        backpackInventories.add(inventory);
    }

    private void unregisterBackpackInventory(Inventory inventory) {
        backpackInventories.remove(inventory);
    }

    private void unloadBackpack(UUID playerId) {
        currentPageIndexMap.remove(playerId);
        ArrayList<Inventory> pages = backpacks.remove(playerId);

        if (pages != null) {
            for (Inventory inv : pages) {
                unregisterBackpackInventory(inv);
            }
        }

        adminViewTargets.entrySet().removeIf(entry -> {
            boolean viewingTarget = entry.getValue().equals(playerId);
            if (viewingTarget) {
                editableAdminViewers.remove(entry.getKey());
                currentPageIndexMap.remove(entry.getKey());
            }
            return viewingTarget;
        });

        playersWithOpenBackpack.remove(playerId);
    }

    public void unloadAllBackpacks() {
        Set<UUID> playerIds = new HashSet<>(backpacks.keySet());

        for (UUID playerId : playerIds) {
            try {
                ArrayList<Inventory> pages = backpacks.get(playerId);
                if (pages != null) {
                    fileHandlers.saveBackpackInventoryForTarget(playerId, pages);
                }
                unloadBackpack(playerId);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to unload backpack for player " + playerId + ": " + e.getMessage());
            }
        }

        adminViewTargets.clear();
        editableAdminViewers.clear();
    }

    /* BACKUP & MAINTENANCE */

    public void createBackup() {
        fileHandlers.createBackup();
    }

    public void saveAllBackpacks() {
        for (UUID playerId : backpacks.keySet()) {
            ArrayList<Inventory> pages = backpacks.get(playerId);
            if (pages != null) {
                fileHandlers.saveBackpackInventoryForTarget(playerId, pages);
            }
        }
    }

}

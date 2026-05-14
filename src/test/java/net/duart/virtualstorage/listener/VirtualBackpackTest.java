package net.duart.virtualstorage.listener;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualBackpackTest {

    @Test
    void rightClickingNavigationArrowCancelsWithoutChangingPage() {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(true, true, true, false, false, false);

        assertTrue(decision.cancelClick());
        assertFalse(decision.changePage());
    }

    @Test
    void allowedNavigationClicksCancelAndChangePage() {
        assertNavigationClickChangesPage(true, false, false);
        assertNavigationClickChangesPage(false, true, false);
    }

    @Test
    void keyboardClicksOnNavigationArrowCancelWithoutChangingPage() {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(true, true, true, false, false, true);

        assertTrue(decision.cancelClick());
        assertFalse(decision.changePage());
    }

    @Test
    void emptyNavigationSlotsCancelWithoutChangingPage() {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(true, false, true, true, false, false);

        assertTrue(decision.cancelClick());
        assertFalse(decision.changePage());
    }

    @Test
    void regularItemsInNavigationSlotsCancelWithoutChangingPage() {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(true, false, true, false, false, false);

        assertTrue(decision.cancelClick());
        assertFalse(decision.changePage());
    }

    @Test
    void stalePageNavigationSlotClicksCancelWithoutChangingPage() {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(true, true, false, true, false, false);

        assertTrue(decision.cancelClick());
        assertFalse(decision.changePage());
    }

    @Test
    void dragsTouchingNavigationSlotsAreBlocked() {
        assertTrue(VirtualBackpack.dragTouchesNavigationSlot(Set.of(45), 54));
        assertTrue(VirtualBackpack.dragTouchesNavigationSlot(Set.of(53), 54));
        assertTrue(VirtualBackpack.dragTouchesNavigationSlot(Set.of(10, 45, 60), 54));
    }

    @Test
    void dragsOutsideBackpackNavigationSlotsAreAllowed() {
        assertFalse(VirtualBackpack.dragTouchesNavigationSlot(Set.of(10, 44, 52), 54));
        assertFalse(VirtualBackpack.dragTouchesNavigationSlot(Set.of(54, 53 + 54), 54));
    }

    @Test
    void readOnlyAdminBackpackClicksAreBlocked() {
        assertTrue(VirtualBackpack.shouldCancelAdminBackpackEdit(false, true));
    }

    @Test
    void editAdminBackpackClicksAreAllowed() {
        assertFalse(VirtualBackpack.shouldCancelAdminBackpackEdit(true, true));
    }

    @Test
    void readOnlyAdminBottomInventoryClicksAreBlockedWhileViewingBackpack() {
        assertTrue(VirtualBackpack.shouldCancelAdminBackpackEdit(false, true));
    }

    @Test
    void dragsTouchingTopInventoryAreDetected() {
        assertTrue(VirtualBackpack.dragTouchesTopInventory(Set.of(0), 54));
        assertTrue(VirtualBackpack.dragTouchesTopInventory(Set.of(53), 54));
        assertFalse(VirtualBackpack.dragTouchesTopInventory(Set.of(54), 54));
    }

    private static void assertNavigationClickChangesPage(boolean leftClick, boolean shiftClick, boolean keyboardClick) {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(true, true, true, leftClick, shiftClick, keyboardClick);

        assertTrue(decision.cancelClick());
        assertTrue(decision.changePage());
    }
}

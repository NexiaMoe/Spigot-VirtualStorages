package net.duart.virtualstorage.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualBackpackTest {

    @Test
    void rightClickingNavigationArrowCancelsWithoutChangingPage() {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(false, false, false);

        assertTrue(decision.cancelClick());
        assertFalse(decision.changePage());
    }

    @Test
    void allowedNavigationClicksCancelAndChangePage() {
        assertNavigationClickChangesPage(true, false, false);
        assertNavigationClickChangesPage(false, true, false);
        assertNavigationClickChangesPage(false, false, true);
    }

    private static void assertNavigationClickChangesPage(boolean leftClick, boolean shiftClick, boolean keyboardClick) {
        VirtualBackpack.NavigationClickDecision decision =
                VirtualBackpack.decideNavigationClick(leftClick, shiftClick, keyboardClick);

        assertTrue(decision.cancelClick());
        assertTrue(decision.changePage());
    }
}

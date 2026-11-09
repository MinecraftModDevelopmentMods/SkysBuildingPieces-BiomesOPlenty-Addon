package zone.moddev.mc.skysbuildingpiecesbop.basket;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BasketPreflightTest {
    @Test
    void emptyNativeRegionIsValidButNonemptyTruncatedHeadersStopRecovery() throws Exception {
        assertFalse(BasketPreflight.hasRegionHeader(0, "r.0.0.mca"));
        for (long size : new long[] {-1, 1, 4096, 8191}) {
            IOException failure = assertThrows(IOException.class,
                    () -> BasketPreflight.hasRegionHeader(size, "r.0.0.mca"));
            assertTrue(failure.getMessage().contains("r.0.0.mca"));
        }
        assertTrue(BasketPreflight.hasRegionHeader(8192, "r.0.0.mca"));
        assertTrue(BasketPreflight.hasRegionHeader(12288, "r.0.0.mca"));
    }
}

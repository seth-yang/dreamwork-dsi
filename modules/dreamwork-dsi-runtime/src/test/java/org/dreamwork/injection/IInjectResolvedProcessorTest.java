package org.dreamwork.injection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IInjectResolvedProcessor} 的单元测试：验证默认的处理顺序语义。
 */
class IInjectResolvedProcessorTest {

    @Test
    void getOrder_defaultsToZero () {
        IInjectResolvedProcessor processor = context -> {};

        assertEquals (0, processor.getOrder ());
    }

    @Test
    void compareTo_sortsByOrderAscending () {
        IInjectResolvedProcessor first = new FixedOrder (10);
        IInjectResolvedProcessor second = new FixedOrder (20);

        assertTrue (first.compareTo (second) < 0);
        assertTrue (second.compareTo (first) > 0);
        assertEquals (0, first.compareTo (new FixedOrder (10)));
    }

    @Test
    void sortingByCompareTo_keepsOrderAscending () {
        List<IInjectResolvedProcessor> processors = new ArrayList<> (List.of (
                new FixedOrder (30),
                new FixedOrder (10),
                new FixedOrder (20)
        ));

        processors.sort (IInjectResolvedProcessor::compareTo);

        assertEquals (List.of (10, 20, 30), processors.stream ().map (IInjectResolvedProcessor::getOrder).toList ());
    }

    private static class FixedOrder implements IInjectResolvedProcessor {
        private final int order;

        FixedOrder (int order) {
            this.order = order;
        }

        @Override
        public void perform (IObjectContext context) {}

        @Override
        public int getOrder () {
            return order;
        }
    }
}

package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 责任链契约：`match` 命中才 `doParse`；`doParse` 返回 null 表示「我处理不了」并交给下一个；
 * 返回非 null 表示「我说了算」，链在这里停止（不会继续找下一个 parser）。
 * <p>
 * 这条契约是动态取值语义的地基：SpElValueParser 的 match 恒为 true，所以它必须排在链尾；
 * 而任何一个 parser 误返回 null（而不是抛异常）都会让值悄悄落到兜底解析器上。
 */
class AbstractChainValueParserTest {

    private static class StubChain extends AbstractChainValueParser {
        private final String prefix;
        private final String result;
        private int doParseCalls;

        StubChain(String prefix, String result) {
            this.prefix = prefix;
            this.result = result;
        }

        @Override
        public boolean match(String value) {
            return value.startsWith(prefix);
        }

        @Override
        protected String doParse(MethodInvocation invocation, String value) {
            doParseCalls++;
            return result;
        }
    }

    @Test
    void fallsThroughToNextParserWhenMatchedParserReturnsNull() {
        StubChain first = new StubChain("#header", null);
        StubChain second = new StubChain("", "fallback");
        first.setNextParser(second);

        assertEquals("fallback", first.parse(null, "#headerX-Tenant-Id"));
        assertEquals(1, first.doParseCalls, "命中的 parser 必须被调用");
    }

    @Test
    void stopsAtFirstNonNullResult() {
        StubChain first = new StubChain("#header", "hit");
        StubChain second = new StubChain("", "fallback");
        first.setNextParser(second);

        assertEquals("hit", first.parse(null, "#headerX-Tenant-Id"));
        assertEquals(1, first.doParseCalls);
        assertEquals(0, second.doParseCalls, "拿到结果后不应继续调用下一个 parser");
    }

    @Test
    void skipsDoParseWhenNotMatched() {
        StubChain first = new StubChain("#header", "unused");
        StubChain second = new StubChain("", "fallback");
        first.setNextParser(second);

        assertEquals("fallback", first.parse(null, "plain-value"));
        assertEquals(0, first.doParseCalls, "不匹配时不应调用 doParse");
    }

    @Test
    void returnsNullWhenNothingMatchesAndThereIsNoNextParser() {
        StubChain only = new StubChain("#header", null);

        assertNull(only.parse(null, "plain-value"), "链尾无人接手时返回 null，由调用方决定后果");
    }
}

package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #header / #session 取值器的真实语义与失败姿态。
 * <p>
 * 关键点：这两个前缀是 startsWith 匹配，header/session 名直接拼在前缀后面（{@code #headerX-Tenant-Id} 取 {@code X-Tenant-Id}）。
 * 取不到值时必须立刻报出「哪个 header/属性没拿到」，而不是返回 null 让责任链把 {@code #headerX-Tenant-Id}
 * 当成字面量、最后在 DatabaseFactory 里抛一句看不懂的异常。
 */
class RequestValueParserTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MethodInvocation invocation = mock(MethodInvocation.class);

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void readsHeaderNamedRightAfterPrefix() {
        bindRequest();
        request.addHeader("X-Tenant-Id", "acme");

        assertEquals("acme", new HeaderValueParser().doParse(invocation, "#headerX-Tenant-Id"));
    }

    @Test
    void missingHeaderFailsWithClearMessage() {
        bindRequest();

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> new HeaderValueParser().doParse(invocation, "#headerX-Tenant-Id"));

        assertTrue(error.getMessage().contains("X-Tenant-Id"), "要点名缺失的 header，实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("#header"), "要说清是哪个取值表达式，实际: " + error.getMessage());
    }

    @Test
    void headerOutsideRequestThreadFailsWithClearMessage() {
        // 异步线程/定时任务里没有绑定请求，此时不能靠 NPE 说话
        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> new HeaderValueParser().doParse(invocation, "#headerX-Tenant-Id"));

        assertTrue(error.getMessage().contains("#header"), "要说清是哪个取值表达式，实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("request"), "要说明缺的是当前请求，实际: " + error.getMessage());
    }

    @Test
    void readsSessionAttribute() {
        bindRequest();
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("tenant")).thenReturn("acme");
        request.setSession(session);

        assertEquals("acme", new SessionValueParser().doParse(invocation, "#sessiontenant"));
    }

    @Test
    void missingSessionAttributeFailsWithClearMessage() {
        bindRequest();
        request.setSession(mock(HttpSession.class));

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> new SessionValueParser().doParse(invocation, "#sessiontenant"));

        assertTrue(error.getMessage().contains("tenant"), "要点名缺失的属性，实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("#session"), "要说清是哪个取值表达式，实际: " + error.getMessage());
    }

    @Test
    void sessionOutsideRequestThreadFailsWithClearMessage() {
        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> new SessionValueParser().doParse(invocation, "#sessiontenant"));

        assertTrue(error.getMessage().contains("#session"), "要说清是哪个取值表达式，实际: " + error.getMessage());
    }

    @Test
    void bothParsersOnlyMatchTheirOwnPrefix() {
        assertTrue(new HeaderValueParser().match("#headerX"));
        assertTrue(!new HeaderValueParser().match("#sessionX"));
        assertTrue(new SessionValueParser().match("#sessionX"));
        assertTrue(!new SessionValueParser().match("#headerX"));
    }
}

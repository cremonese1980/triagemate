package com.triagemate.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JUnit5 extension that captures logback events during test execution.
 * Useful for verifying MDC content and log messages in integration tests.
 *
 * <p>Usage:
 * <pre>
 * &#64;RegisterExtension
 * static LogCaptureExtension logCapture = new LogCaptureExtension();
 * </pre>
 */
public class LogCaptureExtension implements BeforeEachCallback, AfterEachCallback {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    @Override
    public void beforeEach(ExtensionContext context) {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    /**
     * @return all captured log events
     */
    public List<ILoggingEvent> getEvents() {
        return listAppender.list;
    }

    /**
     * @return events whose formatted message contains the given fragment
     */
    public List<ILoggingEvent> getEventsContaining(String messageFragment) {
        return listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains(messageFragment))
                .toList();
    }

    /**
     * @return events that have the given MDC key=value
     */
    public List<ILoggingEvent> getEventsWithMdc(String key, String value) {
        return listAppender.list.stream()
                .filter(e -> {
                    Map<String, String> mdc = e.getMDCPropertyMap();
                    return mdc != null && value.equals(mdc.get(key));
                })
                .toList();
    }
}

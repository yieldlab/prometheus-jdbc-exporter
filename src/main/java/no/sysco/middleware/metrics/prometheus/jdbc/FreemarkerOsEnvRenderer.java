package no.sysco.middleware.metrics.prometheus.jdbc;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

public class FreemarkerOsEnvRenderer implements TemplateRenderer {

    private final Configuration config;
    private final Object data;

    public FreemarkerOsEnvRenderer() {
        config = initConfig();
        data = Map.of("env", Map.copyOf(System.getenv()));
    }

    @Override
    public String render(String template) {
        try {
            final var tpl = new Template(null, template, config);
            final var result = new StringWriter();
            tpl.process(data, result);
            return result.toString();
        } catch (TemplateException | IOException e) {
            throw new RuntimeException("failed to render template", e);
        }
    }

    private static final Configuration initConfig() {
        final var cfg = new Configuration(Configuration.VERSION_2_3_31);
        // Sets how errors will appear.
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        // Don't log exceptions inside FreeMarker that it will thrown at you anyway
        cfg.setLogTemplateExceptions(false);
        // Wrap unchecked exceptions thrown during template processing into TemplateExceptions
        cfg.setWrapUncheckedExceptions(true);
        // Do not fall back to higher scopes when reading a null loop variable
        cfg.setFallbackOnNullLoopVariable(false);
        return cfg;
    }
}

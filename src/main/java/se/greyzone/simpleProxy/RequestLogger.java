package se.greyzone.simpleProxy;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.littleshoot.proxy.HttpRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

public class RequestLogger implements HttpRequestFilter {

    private static Marker logMarkerJMeter = MarkerFactory
            .getMarker("JMETER");
    private static Logger log = LoggerFactory.getLogger(RequestLogger.class);

    private final String testName;
    private final Set<String> logForHostNames = new HashSet<String>();
    private final Set<String> requestURINameFilter = new HashSet<String>();

    private final AtomicInteger requestCounter = new AtomicInteger(1);
    private Configuration freemarkerCfg;

    public RequestLogger(final String testName,
            final Collection<String> hostNames,
            final Collection<String> uriNameFilter) {

        this.testName = testName;

        if (hostNames != null)
            logForHostNames.addAll(hostNames);

        if (uriNameFilter != null)
            requestURINameFilter.addAll(uriNameFilter);

        initFreemarker();
    }

    protected void initFreemarker() {

        freemarkerCfg = new Configuration();
        try {
            freemarkerCfg.setClassForTemplateLoading(getClass(), "/templates");
            freemarkerCfg.setObjectWrapper(new DefaultObjectWrapper());
        } catch (final Exception e) {
            log.error("Failed to initialize FreeMarker", e);
            throw new RuntimeException(e);
        }
    }

    public void filter(final HttpRequest httpRequest) {

        if (!hostNamePassesFilter(httpRequest))
            return;

        if (!uriPassesFilter(httpRequest))
            return;

        logRequest(httpRequest);
    }

    protected boolean hostNamePassesFilter(final HttpRequest httpRequest) {

        if (logForHostNames.isEmpty())
            return true;

        final String currentHost = httpRequest.getHeader("Host");
        if (StringUtils.isEmpty(currentHost))
            return true;

        for (final String host : logForHostNames) {
            if (host.equals(currentHost))
                return true;
        }

        return false;
    }

    protected boolean uriPassesFilter(final HttpRequest httpRequest) {

        if (requestURINameFilter.isEmpty())
            return true;

        final String requestUri = httpRequest.getUri();

        for (final String uriFilter : requestURINameFilter) {
            if (requestUri.contains(uriFilter))
                return true;
        }

        return false;
    }

    protected void logRequest(final HttpRequest httpRequest) {

        log.debug("Logging request for URI: {}", httpRequest.getUri());
        final Map<String, Object> root = getRoot(httpRequest);

        Writer out = null;
        try {
            final Template template = freemarkerCfg
                    .getTemplate("httpSampler.ftl");
            out = new StringWriter();
            template.process(root, out);

            log.info(logMarkerJMeter, out.toString());
        } catch (final Exception e) {
            log.error("Failed to create freemarker template", e);
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (final IOException e) {
                log.error("Failed to close StringWriter", e);
            }
        }

    }

    protected Map<String, Object> getRoot(final HttpRequest httpRequest) {

        final Map<String, Object> root = new HashMap<String, Object>();

        root.put("testname", testName + requestCounter.getAndIncrement());
        root.put("path", httpRequest.getUri());
        root.put("method", httpRequest.getMethod().getName());

        final Tuple<Boolean, List<NameValue>> strippedNameValues = stripNameValueFromList(
                getNamedValueList(httpRequest), "javax.faces.ViewState");

        root.put("nameValuePairs", strippedNameValues.getSecond());
        root.put("includeViewState", strippedNameValues.getFirst());

        return root;
    }

    protected List<NameValue> getNamedValueList(
            final HttpRequest httpRequest) {

        final String queryString = new String(httpRequest.getContent().array());

        final QueryStringDecoder decoder = new QueryStringDecoder("?"
                + queryString);

        final List<NameValue> nameValues = new ArrayList<NameValue>();

        final Map<String, List<String>> parameters = decoder.getParameters();
        for (final Entry<String, List<String>> entry : parameters.entrySet()) {

            final String key = entry.getKey();

            for (final String value : entry.getValue()) {
                log.debug("Adding: {}={}", key, value);
                nameValues.add(new NameValue(key, value));
            }
        }

        return nameValues;
    }

    private Tuple<Boolean, List<NameValue>> stripNameValueFromList(
            final List<NameValue> list,
            final String name) {

        final List<NameValue> result = new ArrayList<NameValue>();
        boolean foundName = false;
        for (final NameValue nv : list) {
            if (!name.equals(nv.getName()))
                result.add(nv);
            else
                foundName = true;
        }

        return new Tuple<Boolean, List<NameValue>>(foundName, result);
    }

    public String getTestName() {

        return testName;
    }

    public Set<String> getLogForHostNames() {

        return logForHostNames;
    }

    public Set<String> getRequestURINameFilter() {

        return requestURINameFilter;
    }

    public class Tuple<T, K> {

        T first;
        K second;

        public Tuple(final T first, final K second) {

            this.first = first;
            this.second = second;
        }

        public T getFirst() {

            return first;
        }

        public K getSecond() {

            return second;
        }
    }
}

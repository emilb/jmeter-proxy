package se.greyzone.simpleProxy;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;

public class RequestParser {

	private final String request;
	private final Configuration configuration;
	
	public RequestParser(String request, Configuration configuration) {
		this.request = request;
		this.configuration = configuration;
	}
	
	public String getMethod() {
		if (request.indexOf("POST ") >= 0)
			return "POST";
		
		if (request.indexOf("GET ") >= 0)
			return "GET";
		
		return "UNKNOWN";
	}
	
	public String getRequestUrl() {
		int posStart = request.indexOf("http");
		int posEnd = request.indexOf(' ', posStart);
		
		return request.substring(posStart, posEnd);
	}
	
	public String getContent() {
		int posTwoNewLines = request.indexOf("\r\n\r\n");
		if (posTwoNewLines < 0)
			return "";
		
		if (posTwoNewLines + 4 >= request.length())
			return "";
		
		return request.substring(posTwoNewLines+4, request.length());
	}
	
	public void parseQueryString() {
		
		List<NameValuePair> nameValues;
		nameValues = getNameValuePairs();
		for (NameValuePair nv : nameValues) {
			System.out.println(nv.getName() + "=" + nv.getValue());
		}
	}
	
	private List<NameValuePair> getNameValuePairs() {
		try {
			return URLEncodedUtils.parse(new URI("http://t.se/?" + getContent()), "UTF-8");
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	private Tuple<Boolean, List<NameValuePair>> stripNameValueFromList(List<NameValuePair> list, String name) {
		List<NameValuePair> result = new ArrayList<NameValuePair>();
		boolean foundName = false;
		for (NameValuePair nv : list) {
			if (!name.equals(nv.getName()))
				result.add(nv);
			else
				foundName = true;
		}
		
		return new Tuple<Boolean, List<NameValuePair>>(foundName, result);
	}
	
	public String getJMeterHttpRequestXML() {
		StringBuilder sb = new StringBuilder();
		
		Tuple<Boolean, List<NameValuePair>> stripNameValueFromList = stripNameValueFromList(getNameValuePairs(), "javax.faces.ViewState");
		
		Map<String, Object> root = new HashMap<String, Object>();
		root.put("testname", "testname");
		root.put("path", getRequestUrl());
		root.put("method", getMethod());
		root.put("nameValuePairs", stripNameValueFromList.getSecond());
		root.put("includeViewState", stripNameValueFromList.getFirst());
		
		try {
//			Template template = configuration.getTemplate("httpSampler.ftl");
//			Writer out = new OutputStreamWriter(System.out);
//			template.process(root, out);
		} catch (Exception e) { e.printStackTrace(); }
		
		return sb.toString();
	}
	
	protected String getJMeterSendParameter(NameValuePair nv) {
		StringBuilder sb = new StringBuilder();
		
		return sb.toString();
	}
	
	public class Tuple<T, K> {
		
		T first;
		K second;
		
		public Tuple(T first, K second) {
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

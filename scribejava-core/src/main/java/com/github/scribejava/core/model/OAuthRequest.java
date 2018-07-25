package com.github.scribejava.core.model;

import com.github.scribejava.core.exceptions.OAuthException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The representation of an OAuth HttpRequest.
 */
public class OAuthRequest {
	/*
	 * The class containing more than one payload of multipart/form-data request
	 */
	public class MultipartPayloads {
		private String boundary;
		private int contentLength;
		private List<MultipartPayload> multipartPayloadList;

		public MultipartPayloads(String boundary) {
			this.boundary = boundary;
			this.multipartPayloadList = new ArrayList<>();
		}
		
		public byte[] getStartBoundary(int index) {
			MultipartPayload multipartPaayLoad = multipartPayloadList.get(index);
			byte[] bytes = ("--" + boundary +"\r\n"
					+ "Content-Disposition: " + multipartPaayLoad.contentDisposition + "\r\n"
					+ (multipartPaayLoad == null ? "" : "Content-Type: " + multipartPaayLoad.contentType + "\r\n")
					+ "\r\n").getBytes();
			return bytes;
		}

		public byte[] getEndBoundary(int index) {
			return ("\r\n"
					+ "--" + boundary + "--\r\n").getBytes();
		}

		public int getContentLength() {
			return contentLength;
		}

		public void addContentLength(int length) {
			this.contentLength += length;
		}

		public List<MultipartPayload> getMultipartPayloadList() {
			return multipartPayloadList;
		}
	}
	
	public class MultipartPayload {
		private String contentDisposition;
		private  String contentType;
		private byte[] payload;
		private int length;

		public MultipartPayload(String contentDisposition, String contentType, byte[] payload, int length) {
			this.contentDisposition = contentDisposition;
			this.contentType = contentType;
			this.payload = payload;
			this.length = length;
		}

		public byte[] getPayload() {
			return payload;
		}
		
		public int getLength() {
			return length;
		}
	}

	private static final String OAUTH_PREFIX = "oauth_";

    private final String url;
    private final Verb verb;
    private final ParameterList querystringParams = new ParameterList();
    private final ParameterList bodyParams = new ParameterList();
    private final Map<String, String> headers = new HashMap<>();

    private String charset;

    private String stringPayload;
    private byte[] byteArrayPayload;
    private File filePayload;
    private MultipartPayloads multipartPayloads;

    private final Map<String, String> oauthParameters = new HashMap<>();

    private String realm;

    /**
     * Default constructor.
     *
     * @param verb Http verb/method
     * @param url resource URL
     */
    public OAuthRequest(Verb verb, String url) {
        this.verb = verb;
        this.url = url;
    }

    /**
     * Adds an OAuth parameter.
     *
     * @param key name of the parameter
     * @param value value of the parameter
     * @throws IllegalArgumentException if the parameter is not an OAuth parameter
     */
    public void addOAuthParameter(String key, String value) {
        oauthParameters.put(checkKey(key), value);
    }

    private String checkKey(String key) {
        if (key.startsWith(OAUTH_PREFIX) || key.equals(OAuthConstants.SCOPE) || key.equals(OAuthConstants.REALM)) {
            return key;
        } else {
            throw new IllegalArgumentException(
                    String.format("OAuth parameters must either be '%s', '%s' or start with '%s'", OAuthConstants.SCOPE,
                            OAuthConstants.REALM, OAUTH_PREFIX));
        }
    }

    public Map<String, String> getOauthParameters() {
        return oauthParameters;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getRealm() {
        return realm;
    }

    /**
     * Returns the complete url (host + resource + encoded querystring parameters).
     *
     * @return the complete url.
     */
    public String getCompleteUrl() {
        return querystringParams.appendTo(url);
    }

    /**
     * Add an HTTP Header to the Request
     *
     * @param key the header name
     * @param value the header value
     */
    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    /**
     * Add a body Parameter (for POST/ PUT Requests)
     *
     * @param key the parameter name
     * @param value the parameter value
     */
    public void addBodyParameter(String key, String value) {
        this.bodyParams.add(key, value);
    }

    /**
     * Add a QueryString parameter
     *
     * @param key the parameter name
     * @param value the parameter value
     */
    public void addQuerystringParameter(String key, String value) {
        this.querystringParams.add(key, value);
    }

    public void addParameter(String key, String value) {
        if (verb.isPermitBody()) {
            bodyParams.add(key, value);
        } else {
            querystringParams.add(key, value);
        }
    }
   

    /*
     * Set boundary of multipart request
     * 
     * @param boundary can be any string
     */
    public void setMultipartBoundary(String boundary) {
    	multipartPayloads = new MultipartPayloads(boundary);
    }
    
    
    /* 
     * Add one multipart form-data payload to the request & increase the current Content-Length
     * 
     * @param contentDisposition value of Content-Disposition header
     * @param contentType value of Content-Type header
     * @param payload data array containing the data to send
     * @param length the max no of bytes to send
     * 
     * Remarks:
     * 57 and 37 are the length of constant portions of contentDisposition and/or contentType headers
     * refer getStartBoundary and getEndBoundary for the constant
     * 
     * Must be called after setMultipartBoundary method
     */ 
    public void addMultipartPayload(String contentDisposition, String contentType, byte[] payload, int length) {
    	int contentLenght;
    	if (contentType == null) 
    		contentLenght = 37 + multipartPayloads.boundary.length() * 2 + contentDisposition.length() + payload.length;
    	else
    		contentLenght = 53 + multipartPayloads.boundary.length() * 2 + contentDisposition.length() + + contentType.length() +  payload.length;
    	multipartPayloads.addContentLength(contentLenght);
    	
    	multipartPayloads.getMultipartPayloadList().add(new MultipartPayload(contentDisposition, contentType, payload, length));
    }
    
    /**
     * Set body payload. This method is used when the HTTP body is not a form-url-encoded string, but another thing.
     * Like for example XML. Note: The contents are not part of the OAuth signature
     *
     * @param payload the body of the request
     */
    public void setPayload(String payload) {
        resetPayload();
        stringPayload = payload;
    }

    /**
     * Overloaded version for byte arrays
     *
     * @param payload byte[]
     */
    public void setPayload(byte[] payload) {
        resetPayload();
        byteArrayPayload = payload.clone();
    }

    /**
     * Overloaded version for File
     *
     * @param payload File
     */
    public void setPayload(File payload) {
        resetPayload();
        filePayload = payload;
    }

    private void resetPayload() {
        stringPayload = null;
        byteArrayPayload = null;
        filePayload = null;
        multipartPayloads = null;
    }

    /**
     * Get a {@link ParameterList} with the query string parameters.
     *
     * @return a {@link ParameterList} containing the query string parameters.
     * @throws OAuthException if the request URL is not valid.
     */
    public ParameterList getQueryStringParams() {
        try {
            final ParameterList result = new ParameterList();
            final String queryString = new URL(url).getQuery();
            result.addQuerystring(queryString);
            result.addAll(querystringParams);
            return result;
        } catch (MalformedURLException mue) {
            throw new OAuthException("Malformed URL", mue);
        }
    }

    /**
     * Obtains a {@link ParameterList} of the body parameters.
     *
     * @return a {@link ParameterList}containing the body parameters.
     */
    public ParameterList getBodyParams() {
        return bodyParams;
    }

    /**
     * Obtains the URL of the HTTP Request.
     *
     * @return the original URL of the HTTP Request
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the URL without the port and the query string part.
     *
     * @return the OAuth-sanitized URL
     */
    public String getSanitizedUrl() {
        if (url.startsWith("http://") && (url.endsWith(":80") || url.contains(":80/"))) {
            return url.replaceAll("\\?.*", "").replaceAll(":80", "");
        } else if (url.startsWith("https://") && (url.endsWith(":443") || url.contains(":443/"))) {
            return url.replaceAll("\\?.*", "").replaceAll(":443", "");
        } else {
            return url.replaceAll("\\?.*", "");
        }
    }

    /**
     * Returns the body of the request (set in {@link #setPayload(java.lang.String)})
     *
     * @return form encoded string
     */
    public String getStringPayload() {
        return stringPayload;
    }

    /**
     * @return the body of the request (set in {@link #setPayload(byte[])} or in
     * {@link #addBodyParameter(java.lang.String, java.lang.String)} )
     */
    public byte[] getByteArrayPayload() {
        if (byteArrayPayload != null) {
            return byteArrayPayload;
        }
        final String body = bodyParams.asFormUrlEncodedString();
        try {
            return body.getBytes(getCharset());
        } catch (UnsupportedEncodingException uee) {
            throw new OAuthException("Unsupported Charset: " + getCharset(), uee);
        }
    }

    public MultipartPayloads getMultipartPayloads() {
    	return multipartPayloads;
    }

    public File getFilePayload() {
        return filePayload;
    }

    @Override
    public String toString() {
        return String.format("@Request(%s %s)", getVerb(), getUrl());
    }

    public Verb getVerb() {
        return verb;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getCharset() {
        return charset == null ? Charset.defaultCharset().name() : charset;
    }

    /**
     * Set the charset of the body of the request
     *
     * @param charsetName name of the charset of the request
     */
    public void setCharset(String charsetName) {
        charset = charsetName;
    }

    public interface ResponseConverter<T> {

        T convert(Response response) throws IOException;
    }

}

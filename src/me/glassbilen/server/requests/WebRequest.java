package me.glassbilen.server.requests;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.json.JSONException;
import org.json.JSONObject;

public class WebRequest {
	private URL url;
	private String requestMethod;
	private ContentType contentType;
	private Map<String, Object> arguments;
	private Map<String, String> headers;
	private boolean useCookies;
	private StringBuilder cookieData;
	
	private String rawData;
	private String boundary;
	
	public WebRequest(String url, String requestMethod, ContentType contentType, boolean useCookies) throws MalformedURLException {
		this.url = new URL(url);
		this.requestMethod = requestMethod;
		this.contentType = contentType;
		this.arguments = new HashMap<>();
		this.headers = new HashMap<>();
		this.useCookies = useCookies;
		this.cookieData = new StringBuilder();
		this.rawData = "";
		this.boundary = "";
	}
	
	public void setURL(String url) throws MalformedURLException {
		this.url = new URL(url);
	}
	
	public URL getURL() {
		return url;
	}
	
	public void clearHeaders() {
		headers.clear();
	}
	
	public void clearArguments() {
		arguments.clear();
	}
		
	public void setHeader(String key, String value){
		headers.put(key, value);
	}
	
	public void setArguement(String key, Object value){
		arguments.put(key, value);
	}
	
	public WebRequestResult connect() throws IOException, JSONException, NoSuchElementException {
		return connect(null);
	}
	
	public WebRequestResult connect(File file) throws IOException, JSONException, NoSuchElementException {
		String data = "";
		
		if(contentType == ContentType.JSON) {
			JSONObject jsonObject = new JSONObject();
			
			for(String key : arguments.keySet()) {
				Object object = arguments.get(key);
				jsonObject.put(key, object);
			}
			
			data = jsonObject.toString();
		} else if(contentType == ContentType.FORM) {
			StringBuilder builder = new StringBuilder();
			
			for(String key : arguments.keySet()) {
				Object object = arguments.get(key);

				if(!builder.toString().isEmpty()) {
					builder.append("&");
				}
				
				builder.append(URLEncoder.encode(key.toString(), "UTF-8") + "=" + URLEncoder.encode(object.toString(), "UTF-8"));
			}
			
			data = builder.toString();
		}
		
		if(!rawData.isEmpty()) {
			data = rawData;
		}
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		
		conn.setDoOutput(true);
		
		conn.setReadTimeout(15000);
		conn.setConnectTimeout(15000);
		
		conn.setRequestMethod(requestMethod);
		
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36");
		
		for(String header : headers.keySet()) {
			conn.setRequestProperty(header, headers.get(header));
		}
		
		if(cookieData.toString().length() > 0) {
			conn.setRequestProperty("Cookie", cookieData.toString());
		}
		
		if(!requestMethod.equalsIgnoreCase("GET")) {
			conn.setRequestProperty("Content-Type", contentType.toString() + (boundary.isEmpty() ? "" : "; boundary=----" + boundary));
			conn.setRequestProperty("Content-Length", String.valueOf(data.length()));
			OutputStream os = conn.getOutputStream();
			os.write(data.getBytes("UTF-8"));
		}
		
		conn.connect();
		
		if(file != null && conn.getInputStream() != null) {
			try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				
				while((read = conn.getInputStream().read(buffer)) > 0) {
					baos.write(buffer, 0, read);
				}
				
				try(ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
					Files.copy(bais, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		} catch(IOException e) {
			if(conn.getErrorStream() != null) {
				in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
			}
		}
		
		if(useCookies) {
			List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
			
			if(cookies != null) {
				for(String cookie : conn.getHeaderFields().get("Set-Cookie")) {
					String cookieData = cookie.substring(0, cookie.indexOf(";"));
					appendCookie(cookieData);
				}
			}
		}
		
		String newData = "";
		
		if(in != null) {
			StringBuilder output = new StringBuilder();;
			String input = "";
			
			while((input = in.readLine()) != null) {
				if(!output.toString().isEmpty()) {
					output.append(System.getProperty("line.separator"));
				}
				
				output.append(input);
			}
			
			newData = output.toString();
		} else {
			//throw new IOException("Failed to get input stream.");
		}
		
		return new WebRequestResult(this, newData, conn.getHeaderFields(), conn.getResponseCode());
	}
	
	public String getRequestMethod() {
		return requestMethod;
	}
	
	public ContentType getContentType() {
		return contentType;
	}
	
	public void setRequestMethod(String requestMethod) {
		this.requestMethod = requestMethod;
	}
	
	public void setContentType(ContentType contentType) {
		this.contentType = contentType;
	}

	private void appendCookie(String cookie) {
		if(!cookieData.toString().isEmpty()) {
			StringBuilder newCookie = new StringBuilder();
			
			for(String data : cookieData.toString().split("; ")) {
				String key = data.split("=")[0];
				
				if(key.equalsIgnoreCase(cookie.split("=")[0])) {
					continue;
				}
				
				if(!newCookie.toString().isEmpty()) {
					newCookie.append("; ");
				}
				
				newCookie.append(data);
			}
			
			cookieData = newCookie;
			
			cookieData.append("; ");
		}
		
		cookieData.append(cookie);
	}
	
	public String getCookies() {
		return cookieData.toString();
	}

	public void setRawData(String string) {
		this.rawData = string;
	}
	
	public void setBoundary(String boundary) {
		this.boundary = boundary;
	}
}
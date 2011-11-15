package se.greyzone.simpleProxy;
/* <!-- in case someone opens this in a browser... --> <pre> */
/*
 * This is a simple multi-threaded Java proxy server
 * for HTTP requests (HTTPS doesn't seem to work, because
 * the CONNECT requests aren't always handled properly).
 * I implemented the class as a thread so you can call it 
 * from other programs and kill it, if necessary (by using 
 * the closeSocket() method).
 *
 * We'll call this the 1.1 version of this class. All I 
 * changed was to separate the HTTP header elements with
 * \r\n instead of just \n, to comply with the official
 * HTTP specification.
 *  
 * This can be used either as a direct proxy to other
 * servers, or as a forwarding proxy to another proxy
 * server. This makes it useful if you want to monitor
 * traffic going to and from a proxy server (for example,
 * you can run this on your local machine and set the
 * fwdServer and fwdPort to a real proxy server, and then
 * tell your browser to use "localhost" as the proxy, and
 * you can watch the browser traffic going in and out).
 *
 * One limitation of this implementation is that it doesn't 
 * close the ProxyThread socket if the client disconnects
 * or the server never responds, so you could end up with
 * a bunch of loose threads running amuck and waiting for
 * connections. As a band-aid, you can set the server socket
 * to timeout after a certain amount of time (use the
 * setTimeout() method in the ProxyThread class), although
 * this can cause false timeouts if a remote server is simply
 * slow to respond.
 *
 * Another thing is that it doesn't limit the number of
 * socket threads it will create, so if you use this on a
 * really busy machine that processed a bunch of requests,
 * you may have problems. You should use thread pools if
 * you're going to try something like this in a "real"
 * application.
 *
 * Note that if you're using the "main" method to run this
 * by itself and you don't need the debug output, it will
 * run a bit faster if you pipe the std output to 'nul'.
 *
 * You may use this code as you wish, just don't pretend 
 * that you wrote it yourself, and don't hold me liable for 
 * anything that it does or doesn't do. If you're feeling 
 * especially honest, please include a link to nsftools.com
 * along with the code. Thanks, and good luck.
 *
 * Julian Robichaux -- http://www.nsftools.com
 */
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;

public class JProxy extends Thread
{
	public static final Set<String> filteredResources = new HashSet<String>();
	static {
		filteredResources.add(".png");
		filteredResources.add(".gif");
		filteredResources.add(".jpg");
		filteredResources.add(".css");
		filteredResources.add(".js");
	}
	
	public static final Set<String> includeOnlyResources = new HashSet<String>();
	static {
		includeOnlyResources.add("seam");
	}
	
	public static final int DEFAULT_PORT = 8080;
	
	private ServerSocket server = null;
	private int thisPort = DEFAULT_PORT;
	private String fwdServer = "";
	private int fwdPort = 0;
	private int ptTimeout = 3000; //ProxyThread.DEFAULT_TIMEOUT;
	private int debugLevel = 0;
	private PrintStream debugOut = System.out;
	private Configuration freemarkerCfg;
	
	
	/* here's a main method, in case you want to run this by itself */
	public static void main (String args[])
	{
		int port = 8080;
		String fwdProxyServer = "";
		int fwdProxyPort = 0;
		
//		if (args.length == 0)
//		{
//			System.err.println("USAGE: java JProxy <port number> [<fwd proxy> <fwd port>]");
//			System.err.println("  <port number>   the port this service listens on");
//			System.err.println("  <fwd proxy>     optional proxy server to forward requests to");
//			System.err.println("  <fwd port>      the port that the optional proxy server is on");
//			System.err.println("\nHINT: if you don't want to see all the debug information flying by,");
//			System.err.println("you can pipe the output to a file or to 'nul' using \">\". For example:");
//			System.err.println("  to send output to the file prox.txt: java JProxy 8080 > prox.txt");
//			System.err.println("  to make the output go away: java JProxy 8080 > nul");
//			return;
//		}
		
		// get the command-line parameters
//		port = Integer.parseInt(args[0]);
		if (args.length > 2)
		{
			fwdProxyServer = args[1];
			fwdProxyPort = Integer.parseInt(args[2]);
		}
		
		// create and start the JProxy thread, using a 20 second timeout
		// value to keep the threads from piling up too much
		System.err.println("  **  Starting JProxy on port " + port + ". Press CTRL-C to end.  **\n");
		JProxy jp = new JProxy(port, fwdProxyServer, fwdProxyPort, 20);
		jp.setDebug(1, System.out);		// or set the debug level to 2 for tons of output
		jp.start();
		
		// run forever; if you were calling this class from another
		// program and you wanted to stop the JProxy thread at some
		// point, you could write a loop that waits for a certain
		// condition and then calls JProxy.closeSocket() to kill
		// the running JProxy thread
		while (true)
		{
			try { Thread.sleep(3000); } catch (Exception e) {}
		}
		
		// if we ever had a condition that stopped the loop above,
		// we'd want to do this to kill the running thread
		//jp.closeSocket();
		//return;
	}
	
	
	/* the proxy server just listens for connections and creates
	 * a new thread for each connection attempt (the ProxyThread
	 * class really does all the work)
	 */
	public JProxy (int port)
	{
		thisPort = port;
		freemarkerCfg = new Configuration();
		try {
			freemarkerCfg.setClassForTemplateLoading(getClass(), "/templates");
			freemarkerCfg.setObjectWrapper(new DefaultObjectWrapper());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public JProxy (int port, String proxyServer, int proxyPort)
	{
		this(port);
		fwdServer = proxyServer;
		fwdPort = proxyPort;
	}
	
	public JProxy (int port, String proxyServer, int proxyPort, int timeout)
	{
		this(port, proxyServer, proxyPort);
		ptTimeout = timeout;
	}
	
	
	/* allow the user to decide whether or not to send debug
	 * output to the console or some other PrintStream
	 */
	public void setDebug (int level, PrintStream out)
	{
		debugLevel = level;
		debugOut = out;
	}
	
	
	/* get the port that we're supposed to be listening on
	 */
	public int getPort ()
	{
		return thisPort;
	}
	 
	
	/* return whether or not the socket is currently open
	 */
	public boolean isRunning ()
	{
		if (server == null)
			return false;
		else
			return true;
	}
	 
	
	/* closeSocket will close the open ServerSocket; use this
	 * to halt a running JProxy thread
	 */
	public void closeSocket ()
	{
		try {
			// close the open server socket
			server.close();
			// send it a message to make it stop waiting immediately
			// (not really necessary)
			/*Socket s = new Socket("localhost", thisPort);
			OutputStream os = s.getOutputStream();
			os.write((byte)0);
			os.close();
			s.close();*/
		}  catch(Exception e)  { 
			if (debugLevel > 0)
				debugOut.println(e);
		}
		
		server = null;
	}
	
	
	public void run()
	{
		try {
			// create a server socket, and loop forever listening for
			// client connections
			server = new ServerSocket(thisPort);
			if (debugLevel > 0)
				debugOut.println("Started JProxy on port " + thisPort);
			
			while (true)
			{
				Socket client = server.accept();
				ProxyThread t = new ProxyThread(client, fwdServer, fwdPort, freemarkerCfg);
				t.setDebug(debugLevel, debugOut);
				t.setTimeout(ptTimeout);
				t.start();
			}
		}  catch (Exception e)  {
			if (debugLevel > 0)
				debugOut.println("JProxy Thread error: " + e);
		}
		
		closeSocket();
	}
	
}


/* 
 * The ProxyThread will take an HTTP request from the client
 * socket and send it to either the server that the client is
 * trying to contact, or another proxy server
 */
class ProxyThread extends Thread
{
	private Socket pSocket;
	private String fwdServer = "";
	private int fwdPort = 0;
	private int debugLevel = 0;
	private Configuration freemarkerCfg;
	private PrintStream debugOut = System.out;
	
	// the socketTimeout is used to time out the connection to
	// the remote server after a certain period of inactivity;
	// the value is in milliseconds -- use zero if you don't want 
	// a timeout
	public static final int DEFAULT_TIMEOUT = 20 * 1000;
	private int socketTimeout = DEFAULT_TIMEOUT;
	
	
	public ProxyThread(Socket s, Configuration freemarkerCfg)
	{
		pSocket = s;
		this.freemarkerCfg = freemarkerCfg;
	}

	public ProxyThread(Socket s, String proxy, int port, Configuration freemarkerCfg)
	{
		this(s, freemarkerCfg);
		fwdServer = proxy;
		fwdPort = port;
	}
	
	
	public void setTimeout (int timeout)
	{
		// assume that the user will pass the timeout value
		// in seconds (because that's just more intuitive)
		socketTimeout = timeout * 1000;
	}


	public void setDebug (int level, PrintStream out)
	{
		debugLevel = level;
		debugOut = out;
	}

	private void printTrace(long startTime, String marker) {
		System.out.println(marker + " time since start: " + (System.currentTimeMillis() - startTime));
	}

	public void run()
	{
		long startTime = System.currentTimeMillis();
		try
		{
			printTrace(startTime, "a");
			
			// client streams (make sure you're using streams that use
			// byte arrays, so things like GIF and JPEG files and file
			// downloads will transfer properly)
			BufferedInputStream clientIn = new BufferedInputStream(pSocket.getInputStream());
			BufferedOutputStream clientOut = new BufferedOutputStream(pSocket.getOutputStream());
			
			// the socket to the remote server
			Socket server = null;
			
			printTrace(startTime, "b");
			
			// other variables
			byte[] request = null;
			byte[] response = null;
			int requestLength = 0;
			int responseLength = 0;
			int pos = -1;
			StringBuilder host = new StringBuilder("");
			String hostName = "";
			int hostPort = 80;
			
			// get the header info (the web browser won't disconnect after
			// it's sent a request, so make sure the waitForDisconnect
			// parameter is false)
			request = getHTTPData(clientIn, host, false);
			requestLength = Array.getLength(request);
			
			printTrace(startTime, "c");
			
			// separate the host name from the host port, if necessary
			// (like if it's "servername:8000")
			hostName = host.toString();
			pos = hostName.indexOf(":");
			if (pos > 0)
			{
				try { hostPort = Integer.parseInt(hostName.substring(pos + 1)); 
					}  catch (Exception e)  { }
				hostName = hostName.substring(0, pos);
			}
			
			printTrace(startTime, "d");
			
			// either forward this request to another proxy server or
			// send it straight to the Host
			try
			{
				if ((fwdServer.length() > 0) && (fwdPort > 0))
				{
					server = new Socket(fwdServer, fwdPort);
				}  else  {
					server = new Socket(hostName, hostPort);
				}
			}  catch (Exception e)  {
				// tell the client there was an error
				String errMsg = "HTTP/1.0 500\nContent Type: text/plain\n\n" + 
								"Error connecting to the server:\n" + e + "\n";
				clientOut.write(errMsg.getBytes(), 0, errMsg.length());
			}
			
			printTrace(startTime, "e");
			
			if (server != null)
			{
				server.setSoTimeout(socketTimeout);
				BufferedInputStream serverIn = new BufferedInputStream(server.getInputStream());
				BufferedOutputStream serverOut = new BufferedOutputStream(server.getOutputStream());
				
				printTrace(startTime, "e1");
				
				// send the request out
				serverOut.write(request, 0, requestLength);
				serverOut.flush();
				
				printTrace(startTime, "e2");
				
				// and get the response; if we're not at a debug level that
				// requires us to return the data in the response, just stream
				// it back to the client to save ourselves from having to
				// create and destroy an unnecessary byte array. Also, we
				// should set the waitForDisconnect parameter to 'true',
				// because some servers (like Google) don't always set the
				// Content-Length header field, so we have to listen until
				// they decide to disconnect (or the connection times out).
				if (debugLevel > 1)
				{
					response = getHTTPData(serverIn, true);
					responseLength = Array.getLength(response);
					printTrace(startTime, "e21");
				}  else  {
					responseLength = streamHTTPData(serverIn, clientOut, true);
					printTrace(startTime, "e22");
				}
				
				printTrace(startTime, "e3");
				
				serverIn.close();
				serverOut.close();
				
				printTrace(startTime, "e4");
			}
			
			printTrace(startTime, "f");
			
			// send the response back to the client, if we haven't already
			if (debugLevel > 1) {
				clientOut.write(response, 0, responseLength);
				clientOut.flush();
			}
			
			printTrace(startTime, "g");
			// if the user wants debug info, send them debug info; however,
			// keep in mind that because we're using threads, the output won't
			// necessarily be synchronous
			if (debugLevel > 0)
			{
				long endTime = System.currentTimeMillis();
//				debugOut.println("Request from " + pSocket.getInetAddress().getHostAddress() + 
//									" on Port " + pSocket.getLocalPort() + 
//									" to host " + hostName + ":" + hostPort + 
//									"\n  (" + requestLength + " bytes sent, " + 
//									responseLength + " bytes returned, " + 
//									Long.toString(endTime - startTime) + " ms elapsed)");
//				debugOut.flush();
			}
			if (debugLevel > 1)
			{
				debugOut.println("REQUEST:\n" + (new String(request)));
				debugOut.println("RESPONSE:\n" + (new String(response)));
				debugOut.flush();
			}
			
			logRequestAndParameters(request);
			
			// close all the client streams so we can listen again
			clientOut.close();
			clientIn.close();
			pSocket.close();
		}  catch (Exception e)  {
			if (debugLevel > 0)
				debugOut.println("Error in ProxyThread: " + e);
			//e.printStackTrace();
		}

		printTrace(startTime, "h");
	}
	
	private boolean isFiltered(String requestUrl) {
		for (String suffix : JProxy.filteredResources)
			if (requestUrl.endsWith(suffix))
				return true;
		
		return false;
	}
	
	private boolean isIncluded(String requestUrl) {
		for (String suffix : JProxy.includeOnlyResources)
			if (requestUrl.contains(suffix))
				return true;
		
		return false;
	}
	
	private void logRequestAndParameters(byte[] request) {
		
		RequestParser rp = new RequestParser(new String(request), freemarkerCfg);
		if (isIncluded(rp.getRequestUrl())) {
		
//			System.out.println(rp.getMethod());
//			System.out.println(rp.getRequestUrl());
//			rp.parseQueryString();
//			System.out.println("");
			
			rp.getJMeterHttpRequestXML();
		}
		
	}
	
	private byte[] getHTTPData (InputStream in, boolean waitForDisconnect)
	{
		// get the HTTP data from an InputStream, and return it as
		// a byte array
		// the waitForDisconnect parameter tells us what to do in case
		// the HTTP header doesn't specify the Content-Length of the
		// transmission
		StringBuilder foo = new StringBuilder("");
		return getHTTPData(in, foo, waitForDisconnect);
	}
	
	
	private byte[] getHTTPData (InputStream in, StringBuilder host, boolean waitForDisconnect)
	{
		// get the HTTP data from an InputStream, and return it as
		// a byte array, and also return the Host entry in the header,
		// if it's specified -- note that we have to use a StringBuilder
		// for the 'host' variable, because a String won't return any
		// information when it's used as a parameter like that
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		streamHTTPData(in, bs, host, waitForDisconnect);
		return bs.toByteArray();
	}
	

	private int streamHTTPData (InputStream in, OutputStream out, boolean waitForDisconnect)
	{
		StringBuilder foo = new StringBuilder("");
		return streamHTTPData(in, out, foo, waitForDisconnect);
	}
	
	private int streamHTTPData (InputStream in, OutputStream out, 
									StringBuilder host, boolean waitForDisconnect)
	{
		long startTime = System.currentTimeMillis();
		printTrace(startTime, "stream 1");
		
		// get the HTTP data from an InputStream, and send it to
		// the designated OutputStream
		StringBuilder header = new StringBuilder("");
		String data = "";
		int responseCode = 200;
		int contentLength = 0;
		int pos = -1;
		int byteCount = 0;

		try
		{
			// get the first line of the header, so we know the response code
			data = readLine(in);
			
			if (data != null)
			{
				header.append(data + "\r\n");
				pos = data.indexOf(" ");
				if ((data.toLowerCase().startsWith("http")) && 
					(pos >= 0) && (data.indexOf(" ", pos+1) >= 0))
				{
					String rcString = data.substring(pos+1, data.indexOf(" ", pos+1));
					try
					{
						responseCode = Integer.parseInt(rcString);
					}  catch (Exception e)  {
						if (debugLevel > 0)
							debugOut.println("Error parsing response code " + rcString);
					}
				}
			}
			
			printTrace(startTime, "stream 2");
			
			// get the rest of the header info
			while ((data = readLine(in)) != null)
			{
				// the header ends at the first blank line
				if (data.length() == 0)
					break;
				header.append(data + "\r\n");
				
				// check for the Host header
				pos = data.toLowerCase().indexOf("host:");
				if (pos >= 0)
				{
					host.setLength(0);
					host.append(data.substring(pos + 5).trim());
				}
				
				// check for the Content-Length header
				pos = data.toLowerCase().indexOf("content-length:");
				if (pos >= 0)
					contentLength = Integer.parseInt(data.substring(pos + 15).trim());
			}
			
			printTrace(startTime, "stream 3");
			
			// add a blank line to terminate the header info
			header.append("\r\n");
			
			// convert the header to a byte array, and write it to our stream
			out.write(header.toString().getBytes(), 0, header.length());
			
			// if the header indicated that this was not a 200 response,
			// just return what we've got if there is no Content-Length,
			// because we may not be getting anything else
			if ((responseCode != 200) && (contentLength == 0))
			{
				out.flush();
				return header.length();
			}

			printTrace(startTime, "stream 4");
			
			// get the body, if any; we try to use the Content-Length header to
			// determine how much data we're supposed to be getting, because 
			// sometimes the client/server won't disconnect after sending us
			// information...
			if (contentLength > 0)
				waitForDisconnect = false;
			
			System.out.println("contentLength: " + contentLength);
			
			if ((contentLength > 0) || (waitForDisconnect))
			{
				try {
					byte[] buf = new byte[16384];
					int bytesIn = 0;
					while ( ((byteCount < contentLength) || (waitForDisconnect)) 
							&& ((bytesIn = in.read(buf)) >= 0) )
					{
						out.write(buf, 0, bytesIn);
						byteCount += bytesIn;
						out.flush();
					}
				}  catch (Exception e)  {
					String errMsg = "Error getting HTTP body: " + e;
					if (debugLevel > 0)
						debugOut.println(errMsg);
					//bs.write(errMsg.getBytes(), 0, errMsg.length());
				}
			}
			
			printTrace(startTime, "stream 5");
		}  catch (Exception e)  {
			if (debugLevel > 0)
				debugOut.println("Error getting HTTP data: " + e);
		}
		
		//flush the OutputStream and return
		try  {  out.flush();  }  catch (Exception e)  {}
		
//		if (isPost)
//			System.out.println(header.toString());
		
		return (header.length() + byteCount);
	}
	
	
	private String readLine (InputStream in)
	{
		// reads a line of text from an InputStream
		StringBuilder data = new StringBuilder("");
		int c;
		
		try
		{
			// if we have nothing to read, just return null
			in.mark(1);
			if (in.read() == -1)
				return null;
			else
				in.reset();
			
			while ((c = in.read()) >= 0)
			{
				// check for an end-of-line character
				if ((c == 0) || (c == 10) || (c == 13))
					break;
				else
					data.append((char)c);
			}
		
			// deal with the case where the end-of-line terminator is \r\n
			if (c == 13)
			{
				in.mark(1);
				if (in.read() != 10)
					in.reset();
			}
		}  catch (Exception e)  {
			if (debugLevel > 0)
				debugOut.println("Error getting header: " + e);
		}
		
		// and return what we have
		return data.toString();
	}
	
}

/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2011 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.artifactory.cli.rest;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.io.output.TeeOutputStream;
import org.artifactory.cli.common.RemoteCommandException;
import org.artifactory.cli.main.CliLog;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.*;

/**
 * Provides a comfortable API to the different rest commands
 *
 * @author Noam Tenne
 */
public abstract class RestClient {
    //TODO: [by yl] Use com.sun.jersey.api.client.WebResource instead of commons-httpclient

    /**
     * URL for Rest API
     */
    public static final String SYSTEM_URL = "system";
    public static final String CONFIG_URL = SYSTEM_URL + "/configuration";
    public static final String EXPORT_URL = "export/system";
    public static final String IMPORT_URL = "import/system";
    public static final String SECURITY_URL = SYSTEM_URL + "/security";
    public static final String COMPRESS_URL = SYSTEM_URL + "/storage/compress";

    private RestClient() {
        // utility class
    }

    /**
     * Get method with full settings
     *
     * @param uri                Target URL
     * @param expectedStatus     The expected return status
     * @param expectedResultType The expected media type of the returned data
     * @param printStream        True if should print response stream to system.out
     * @param timeout            Request timeout
     * @param credentials        For authentication
     * @return byte[] - Response stream
     * @throws IOException
     * @throws RemoteCommandException
     */
    public static byte[] get(String uri, int expectedStatus, String expectedResultType, boolean printStream,
                             int timeout, Credentials credentials) throws IOException {
        GetMethod method;
        try {
            method = new GetMethod(uri);
        } catch (IllegalStateException ise) {
            throw new RemoteCommandException("\nAn error has occurred while trying to resolve the given url: " +
                    uri + "\n" + ise.getMessage());
        }
        return executeMethod(uri, method, expectedStatus, expectedResultType, timeout, credentials, printStream);
    }

    /**
     * Get method with receiving customize Get method
     *
     * @param uri
     * @param expectedStatus
     * @param expectedResultType
     * @param printStream
     * @param timeout
     * @param credentials
     * @param method
     * @return
     * @throws Exception
     */
    public static byte[] get(String uri, int expectedStatus, String expectedResultType, boolean printStream,
                             int timeout, Credentials credentials, GetMethod method) throws IOException {
        return executeMethod(uri, method, expectedStatus, expectedResultType, timeout, credentials, printStream);
    }

    public static String getString(String uri, String username, String password) throws IOException {
        byte[] bytes = get(uri, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
        if (bytes == null) {
            return null;
        }
        return new String(bytes, "utf-8");
    }

    public static byte[] get(String uri, String username, String password) throws IOException {
        return get(uri, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
    }

    public static byte[] post(String uri, byte[] input, String username, String password) throws IOException {
        return post(uri, input, null, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
    }

    public static byte[] post(String uri, RequestEntity requestEntity, String username, String password)
            throws IOException {
        return post(uri, requestEntity, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
    }

    public static void delete(String uri, String username, String password) throws IOException {
        delete(uri, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
    }

    public static byte[] delete(String uri, int expectedStatus, String expectedResultType, boolean printStream,
                                int timeout, Credentials credentials) throws IOException {
        DeleteMethod method;
        try {
            method = new DeleteMethod(uri);
        } catch (IllegalStateException ise) {
            throw new RemoteCommandException("\nAn error has occurred while trying to resolve the given url: " +
                    uri + "\n" + ise.getMessage());
        }
        return executeMethod(uri, method, expectedStatus, expectedResultType, timeout, credentials, printStream);
    }

    public static byte[] put(String uri, File input, String username, String password) throws IOException {
        return put(uri, new BufferedInputStream(new FileInputStream(input)), username, password);
    }

    public static byte[] put(String uri, byte[] input, String username, String password) throws IOException {
        return put(uri, new ByteArrayInputStream(input), username, password);
    }

    public static byte[] put(String uri, InputStream input, String username, String password) throws IOException {
        return put(uri, input, null, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
    }

    public static byte[] put(String uri, RequestEntity requestEntity, String username, String password)
            throws IOException {
        return put(uri, requestEntity, HttpStatus.SC_OK, null, false, -1, getCredentials(username, password));
    }

    public static byte[] put(String uri, InputStream input, final String inputType, int expectedStatus,
                             String expectedResultType, boolean printStream, int timeout, Credentials credentials) throws IOException {
        return put(uri, new InputStreamRequestEntity(input, inputType), expectedStatus, expectedResultType,
                printStream, timeout, credentials);
    }

    public static byte[] put(String uri, RequestEntity requestEntity, int expectedStatus,
                             String expectedResultType, boolean printStream, int timeout, Credentials credentials) throws IOException {
        PutMethod method = new PutMethod(uri);
        method.setRequestEntity(requestEntity);
        return executeMethod(uri, method, expectedStatus, expectedResultType, timeout, credentials, printStream);
    }

    /**
     * post method with full settings
     *
     * @param uri                Target URL
     * @param data               Data to send
     * @param inputType          Type of data which is sent
     * @param expectedStatus     Expected return status
     * @param expectedResultType Expected response media type
     * @param printStream        True if should print response stream to system.out
     * @param timeout            Request timeout
     * @param credentials        For authentication
     * @return byte[] - Response stream
     * @throws Exception
     */
    public static byte[] post(String uri, final byte[] data, final String inputType, int expectedStatus,
                              String expectedResultType, boolean printStream, int timeout, Credentials credentials) throws IOException {
        RequestEntity requestEntity = new RequestEntity() {
            public boolean isRepeatable() {
                return true;
            }

            public void writeRequest(OutputStream out) throws IOException {
                if (data != null) {
                    out.write(data);
                }
            }

            public long getContentLength() {
                if (data != null) {
                    return data.length;
                }
                return 0;
            }

            public String getContentType() {
                return inputType;
            }
        };
        return post(uri, requestEntity, expectedStatus, expectedResultType, printStream, timeout, credentials);
    }

    /**
     * post method with full settings
     *
     * @param uri                Target URL
     * @param requestEntity      Request entity to provide the method with
     * @param expectedStatus     Expected return status
     * @param expectedResultType Expected response media type
     * @param printStream        True if should print response stream to system.out
     * @param timeout            Request timeout
     * @param credentials        For authentication
     * @return byte[] - Response stream
     * @throws Exception
     */
    public static byte[] post(String uri, RequestEntity requestEntity, int expectedStatus,
                              String expectedResultType, boolean printStream, int timeout, Credentials credentials) throws IOException {
        PostMethod method = new PostMethod(uri);
        method.setRequestEntity(requestEntity);
        return executeMethod(uri, method, expectedStatus, expectedResultType, timeout, credentials, printStream);
    }

    /**
     * Executes a configured HTTP
     *
     * @param uri                Target URL
     * @param method             Method to execute
     * @param expectedStatus     Expected return status
     * @param expectedResultType Expected response media type
     * @param timeout            Request timeout
     * @param credentials        For authentication
     * @throws Exception
     */
    private static byte[] executeMethod(String uri, HttpMethod method, int expectedStatus, String expectedResultType,
                                        int timeout, Credentials credentials, boolean printStream) throws IOException {
        try {
            getHttpClient(uri, timeout, credentials).executeMethod(method);
            checkStatus(uri, expectedStatus, method);
            Header contentTypeHeader = method.getResponseHeader("content-type");
            if (contentTypeHeader != null) {
                //Check result content type
                String contentType = contentTypeHeader.getValue();
                checkContentType(uri, expectedResultType, contentType);
            }
            return analyzeResponse(method, printStream);
        } catch (SSLException ssle) {
            throw new RemoteCommandException("\nThe host you are trying to reach does not support SSL.");
        } catch (ConnectTimeoutException cte) {
            throw new RemoteCommandException("\n" + cte.getMessage());
        } catch (UnknownHostException uhe) {
            throw new RemoteCommandException("\nThe host of the specified URL: " + uri + " could not be found.\n" +
                    "Please make sure you have specified the correct path. The default should be:\n" +
                    "http://myhost:8081/artifactory/api/system");
        } catch (ConnectException ce) {
            throw new RemoteCommandException("\nCannot not connect to: " + uri + ". " +
                    "Please make sure to specify a valid host (--host <host>:<port>) or URL (--url http://...).");
        } catch (NoRouteToHostException nrthe) {
            throw new RemoteCommandException("\nCannot reach: " + uri + ".\n" +
                    "Please make sure that the address is valid and that the port is open (firewall, router, etc').");
        } finally {
            method.releaseConnection();
        }
    }

    /**
     * Writes the response stream to the selected outputs
     *
     * @param method      The method that was executed
     * @param printStream True if should print response stream to system.out
     * @return byte[] Response
     * @throws IOException
     */
    private static byte[] analyzeResponse(HttpMethod method, boolean printStream) throws IOException {
        InputStream is = method.getResponseBodyAsStream();
        if (is == null) {
            return null;
        }
        byte[] buffer = new byte[1024];
        int r;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream os = baos;
            if (printStream) {
                os = new TeeOutputStream(baos, System.out);
            }
            while ((r = is.read(buffer)) != -1) {
                os.write(buffer, 0, r);
            }
            if (printStream) {
                System.out.println("");
            }
            return baos.toByteArray();
        } catch (SocketTimeoutException e) {
            CliLog.error("Communication with the server has timed out: " + e.getMessage());
            CliLog.error("ATTENTION: The command on the server may still be running!");
            String url = method.getURI().toString();
            int apiPos = url.indexOf("/api");
            String logsUrl;
            if (apiPos != -1) {
                logsUrl = url.substring(0, apiPos) + "/webapp/systemlogs.html";
            } else {
                logsUrl = "http://" + method.getURI().getHost() + "/artifactory/webapp/systemlogs.html";
            }
            CliLog.error("Please check the server logs " + logsUrl + " before re-running the command.");
            return null;
        }
    }

    /**
     * Validates the expected content type
     *
     * @param uri          Target URL
     * @param expectedType Expected response media type
     * @param contentType  Actual response media type
     */
    private static void checkContentType(String uri, String expectedType, String contentType) {
        if (expectedType == null || expectedType.trim().length() == 0) {
            // No type, nothing to check
            return;
        }
        if (!contentType.contains(expectedType)) {
            throw new RuntimeException("HTTP content type was " + contentType + " and should be " +
                    expectedType + " for request on " + uri);
        }
    }

    private static UsernamePasswordCredentials getCredentials(String username, String password) {
        if (username == null) {
            return null;
        }
        return new UsernamePasswordCredentials(username, password);
    }

    /**
     * Validates the expected returned status
     *
     * @param uri            Target URL
     * @param expectedStatus Expected returned status
     * @param method         The method after execution (holds the returned status)
     */
    private static void checkStatus(String uri, int expectedStatus, HttpMethod method) {
        int status = method.getStatusCode();
        if (status != expectedStatus) {
            throw new RemoteCommandException("\nUnexpected response status for request: " + uri + "\n" +
                    "Expected status: " + expectedStatus + " (" + HttpStatus.getStatusText(expectedStatus) + ")" +
                    "\n " +
                    "Received status: " + status + " (" + HttpStatus.getStatusText(status) + ") - " +
                    method.getStatusText() + "\n");
        }
    }

    /**
     * Returnes an HTTP client object with the given configurations
     *
     * @param url         Target URL
     * @param timeout     Request timeout
     * @param credentials For authentication
     * @return HttpClient - Configured client
     * @throws Exception
     */
    private static HttpClient getHttpClient(String url, int timeout, Credentials credentials) {
        HttpConnectionManager connectionManager = new SimpleHttpConnectionManager();
        HttpConnectionManagerParams connectionManagerParams = connectionManager.getParams();
        //Set the socket connection timeout
        connectionManagerParams.setConnectionTimeout(3000);
        HttpClient client = new HttpClient(connectionManager);
        HttpClientParams clientParams = client.getParams();
        //Set the socket data timeout
        int to = 60000;
        if (timeout > 0) {
            to = timeout;
        }
        clientParams.setSoTimeout(to);

        if (credentials != null) {
            String host;
            try {
                host = new URL(url).getHost();
            } catch (MalformedURLException mue) {
                throw new RemoteCommandException("\nAn error has occurred while trying to resolve the given url: " +
                        url + "\n" + mue.getMessage());
            }
            clientParams.setAuthenticationPreemptive(true);
            AuthScope scope = new AuthScope(host, AuthScope.ANY_PORT, AuthScope.ANY_REALM);
            client.getState().setCredentials(scope, credentials);
        }
        return client;
    }
}
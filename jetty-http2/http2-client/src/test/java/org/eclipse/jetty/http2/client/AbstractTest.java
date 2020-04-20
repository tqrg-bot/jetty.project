//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.http2.client;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpFieldsBuilder;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.FlowControlStrategy;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.http2.server.RawHTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.AfterEach;

public class AbstractTest
{
    protected ServerConnector connector;
    protected String servletPath = "/test";
    protected HTTP2Client client;
    protected Server server;

    protected void start(HttpServlet servlet) throws Exception
    {
        HTTP2ServerConnectionFactory connectionFactory = new HTTP2ServerConnectionFactory(new HttpConfiguration());
        connectionFactory.setInitialSessionRecvWindow(FlowControlStrategy.DEFAULT_WINDOW_SIZE);
        connectionFactory.setInitialStreamRecvWindow(FlowControlStrategy.DEFAULT_WINDOW_SIZE);
        prepareServer(connectionFactory);
        ServletContextHandler context = new ServletContextHandler(server, "/", true, false);
        context.addServlet(new ServletHolder(servlet), servletPath + "/*");
        customizeContext(context);
        server.start();

        prepareClient();
        client.start();
    }

    protected void customizeContext(ServletContextHandler context)
    {
    }

    protected void start(ServerSessionListener listener) throws Exception
    {
        RawHTTP2ServerConnectionFactory connectionFactory = new RawHTTP2ServerConnectionFactory(new HttpConfiguration(), listener);
        connectionFactory.setInitialSessionRecvWindow(FlowControlStrategy.DEFAULT_WINDOW_SIZE);
        connectionFactory.setInitialStreamRecvWindow(FlowControlStrategy.DEFAULT_WINDOW_SIZE);
        prepareServer(connectionFactory);
        server.start();

        prepareClient();
        client.start();
    }

    protected void prepareServer(ConnectionFactory... connectionFactories)
    {
        QueuedThreadPool serverExecutor = new QueuedThreadPool();
        serverExecutor.setName("server");
        server = new Server(serverExecutor);
        connector = new ServerConnector(server, 1, 1, connectionFactories);
        server.addConnector(connector);
    }

    protected void prepareClient()
    {
        client = new HTTP2Client();
        QueuedThreadPool clientExecutor = new QueuedThreadPool();
        clientExecutor.setName("client");
        client.setExecutor(clientExecutor);
        client.setInitialSessionRecvWindow(FlowControlStrategy.DEFAULT_WINDOW_SIZE);
        client.setInitialStreamRecvWindow(FlowControlStrategy.DEFAULT_WINDOW_SIZE);
    }

    protected Session newClient(Session.Listener listener) throws Exception
    {
        String host = "localhost";
        int port = connector.getLocalPort();
        InetSocketAddress address = new InetSocketAddress(host, port);
        FuturePromise<Session> promise = new FuturePromise<>();
        client.connect(address, listener, promise);
        return promise.get(5, TimeUnit.SECONDS);
    }

    protected MetaData.Request newRequest(String method, HttpFieldsBuilder fields)
    {
        return newRequest(method, "", fields);
    }

    protected MetaData.Request newRequest(String method, String pathInfo, HttpFieldsBuilder fields)
    {
        String host = "localhost";
        int port = connector.getLocalPort();
        String authority = host + ":" + port;
        return new MetaData.Request(method, HttpScheme.HTTP.asString(), new HostPortHttpField(authority), servletPath + pathInfo, HttpVersion.HTTP_2, fields, -1);
    }

    @AfterEach
    public void dispose() throws Exception
    {
        if (client != null)
            client.stop();
        if (server != null)
            server.stop();
    }
}

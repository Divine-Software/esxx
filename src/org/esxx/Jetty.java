/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.esxx;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.management.ManagementFactory;
import org.eclipse.jetty.ajp.Ajp13SocketConnector;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.esxx.request.AsyncServletRequest;

public abstract class Jetty {
  public static void runJettyServer(int http_port, int ajp_port, URI fs_root_uri)
    throws Exception {
    ESXX esxx = ESXX.getInstance();

    Log.setLog(new JavaUtilLog(esxx.getLogger().getName()));

    int timeout = (int) (Double.parseDouble(esxx.getSettings()
					    .getProperty("esxx.net.timeout", "60"))
			 * 1000);

    Server server = new Server();

    if (http_port != -1) {
      SelectChannelConnector http = new SelectChannelConnector();
      http.setPort(http_port);
      http.setMaxIdleTime(timeout);
      server.addConnector(http);
    }

    if (ajp_port != -1) {
      Ajp13SocketConnector ajp = new Ajp13SocketConnector();
      ajp.setPort(ajp_port);
      ajp.setMaxIdleTime(timeout);
      server.addConnector(ajp);
    }

    MBeanContainer cont = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    server.getContainer().addEventListener(cont);
    server.addBean(cont);
    cont.addBean(Log.getLog());

    server.setThreadPool(new ExecutorThreadPool(esxx.getExecutor()));
    server.setStopAtShutdown(true);

    server.setHandler(new Handler(fs_root_uri));

    server.start();
    server.join();
  }

  private static class Handler // Using a non-anonymous class makes the JMX name look better
    extends AbstractHandler {

    public Handler(URI fs_root_uri) {
      fsRootURI = fs_root_uri;
    }

    @Override
    public void handle(String                           target,
		       org.eclipse.jetty.server.Request req,
		       HttpServletRequest               sreq,
		       HttpServletResponse              sres)
      throws IOException, ServletException {
      req.setHandled(true);
      AsyncServletRequest.handleServletRequest(sreq, sres, fsRootURI, "Jetty Error");
    }

    private URI fsRootURI;
  }
}

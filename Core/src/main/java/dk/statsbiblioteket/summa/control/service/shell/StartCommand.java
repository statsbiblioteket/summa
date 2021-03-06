/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.summa.control.service.shell;

import dk.statsbiblioteket.summa.common.shell.Command;
import dk.statsbiblioteket.summa.common.shell.ShellContext;
import dk.statsbiblioteket.summa.control.api.Service;
import dk.statsbiblioteket.util.rpc.ConnectionContext;
import dk.statsbiblioteket.util.rpc.ConnectionManager;

/**
 * Created by IntelliJ IDEA. User: mikkel Date: Aug 8, 2008 Time: 8:49:41 AM To
 * change this template use File | Settings | File Templates.
 */
public class StartCommand  extends Command {

    private ConnectionManager<Service> cm;
    private String address;

    public StartCommand (ConnectionManager<Service> cm,
                        String serviceAddress) {
        super("start", "Start the service");
        this.cm = cm;
        this.address = serviceAddress;
    }

    @Override
    public void invoke(ShellContext ctx) throws Exception {
        ConnectionContext<Service> connCtx = null;

        ctx.prompt ("Starting service at '" + address + "'...");

        /* Get a connection */
        try {
            connCtx = cm.get (address);
        } catch (Exception e){
            ctx.error ("Failed to connect to '" + address + "'. Error was: "
                       + e.getMessage());
            throw new RuntimeException("Failed to connect to '" + address + "'",
                                       e);
        }

        /* Start the service */
        try {
            Service service = connCtx.getConnection();
            service.start();
        } finally {
            if (connCtx != null) {
                cm.release (connCtx);
                ctx.info("OK");
            } else {
                ctx.error ("Failed to connect, unknown error");
            }
        }
    }

}






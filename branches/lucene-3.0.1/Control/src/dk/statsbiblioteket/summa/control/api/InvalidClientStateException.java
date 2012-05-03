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
package dk.statsbiblioteket.summa.control.api;

/**
 * Thrown when connecting to a client that is either not running
 * or has a broken connection.
 */
public class InvalidClientStateException extends RuntimeException {

    private String clientId;

    public InvalidClientStateException (String clientId, String msg) {
        super (msg);
        this.clientId = clientId;
    }

    public InvalidClientStateException (String clientId, String msg,
                                        Throwable cause) {
        super (msg, cause);
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

}




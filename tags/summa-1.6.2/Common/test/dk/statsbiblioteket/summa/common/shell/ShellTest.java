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
package dk.statsbiblioteket.summa.common.shell;

import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.summa.common.shell.notifications.SyntaxErrorNotification;
import dk.statsbiblioteket.summa.common.shell.commands.Exec;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Stack;
import java.io.IOException;

import junit.framework.TestCase;
import jline.ConsoleReader;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public class ShellTest extends TestCase {

    Core core;
    ShellContext ctx;

    public void setUp () throws Exception {        
        ctx = new ShellContext () {
                private Stack<String> lineBuffer = new Stack<String>();
                private ConsoleReader lineIn =  createConsoleReader();
                private String lastError = null;

                public void error(String msg) {
                    lineBuffer.clear();
                    lastError = msg;
                    System.out.println ("[ERROR] " + msg);
                }

                public void info(String msg) {
                    System.out.println (msg);
                }

                public void warn(String msg) {
                    System.out.println ("[WARNING] " + msg);
                }

                public void debug(String msg) {
                    System.out.println ("[DEBUG] " + msg);
                }

                public String readLine() {
                    if (!lineBuffer.empty()) {
                        return lineBuffer.pop();
                    }

                    try {
                        return lineIn.readLine().trim();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read input", e);
                    }
                }

                public void pushLine (String line) {
                    lineBuffer.push(line.trim());
                }

                public String getLastError () {
                    return lastError;
                }

                public void prompt (String prompt) {
                    System.out.print(prompt);
                }

                @Override
                public void clear() {
                  try {
                    lineIn.clearScreen();
                  } catch(IOException e) {
                    error("clearing screen");
                  }
                }
            };

        core = new Core (ctx, true, true);
    }

    private static ConsoleReader createConsoleReader() {
        try {
            return new ConsoleReader();
        } catch (IOException e) {
            throw new RuntimeException("Unnable to create ConsoleReader: "
                                       + e.getMessage(), e);
        }
    }

    public void tearDown () throws Exception {

    }

    public void testTokenizer () throws Exception {
        String[] result;
        String cmd;

        cmd = "foo";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo"}));

        cmd = "foo bar";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo", "bar"}));

        cmd = "foo \"bar\"";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo", "bar"}));

        cmd = "\"foo\" bar";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo", "bar"}));

        cmd = "\"foo bar\"";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo bar"}));

        cmd = "\"foo bar\" baz";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo bar", "baz"}));

        cmd = "\"foo    bar\"   baz   ";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo    bar", "baz"}));

        cmd = "\"foo    bar\"   \"baz bug\"";
        result = core.tokenize(cmd);
        assertTrue(Arrays.equals(result, new String[]{"foo    bar", "baz bug"}));
    }

    public void testTokenizerErrors () throws Exception {
        String cmd;

        cmd = "\"foo";
        try {
            core.tokenize(cmd);
            fail ("Tokenizing '" + cmd + "' should raise an exception");
        } catch (SyntaxErrorNotification e) {
            // success
            System.out.println ("Got expected error: " + e.getMessage());
        }

        cmd = "foo\"";
        try {
            core.tokenize(cmd);
            fail ("Tokenizing '" + cmd + "' should raise an exception");
        } catch (SyntaxErrorNotification e) {
            // success
            System.out.println ("Got expected error: " + e.getMessage());
        }

        cmd = "f\"oo";
        try {
            core.tokenize(cmd);
            fail ("Tokenizing '" + cmd + "' should raise an exception");
        } catch (SyntaxErrorNotification e) {
            // success
            System.out.println ("Got expected error: " + e.getMessage());
        }


    }

    public void testScript () throws Exception {
        Script sc;
        String[] stmts;
        Iterator<String> iter;

        /* Check semi-colon delimiting */
        sc = new Script("foo; bar");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        sc = new Script("  foo  ;    bar");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        /* Check compact layout and trailing ; */
        sc = new Script("foo;bar;");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        /* Check newline delimiting */
        sc = new Script("foo\nbar");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        /* Three statements and a trailing \n */
        sc = new Script("foo\nbar\nbaz\n");
        stmts = new String[] {"foo", "bar", "baz"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        /* Check escapes */
        sc = new Script("foo  \\;    bar");
        stmts = new String[] {"foo  ;    bar"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        sc = new Script("foo  \\n    bar");
        stmts = new String[] {"foo  \n    bar"};
        iter = sc.iterator();
        for (String stmt : stmts) {
            assertEquals(stmt, iter.next());
        }

        /* Empty scripts */
        sc = new Script("\n");
        iter = sc.iterator();
        assertFalse(iter.hasNext());

        sc = new Script(";");
        iter = sc.iterator();
        for (String s : sc) {
            System.out.println ("TOK"+s);
        }
        assertFalse(iter.hasNext());
    }

    public void testScriptTokens () throws Exception {
        Script sc;

        sc = new Script(new String[] {"foo", "-w"});
        assertEquals("Should have exactly one statement",
                     1, sc.getStatements().size());
        assertEquals("Should have the statement 'foo -w'",
                     "foo -w", sc.getStatements().get(0));

        sc = new Script(new String[] {"foo", "-w"}, 1);
        assertEquals("Should have exactly one statement",
                     1, sc.getStatements().size());
        assertEquals("Should have the statement '-w'",
                     "-w", sc.getStatements().get(0));
    }

    public void testShellContextPushLine () throws Exception {
        ctx.pushLine("foo");
        ctx.pushLine("bar\n");
        ctx.pushLine("baz;quiz");

        String line = ctx.readLine();
        assertEquals(line, "baz;quiz");

        line = ctx.readLine();
        assertEquals(line, "bar");

        line = ctx.readLine();
        assertEquals(line, "foo");
    }

    public void testExecCommand () throws Exception {
        new Exec();
        Script script = new Script ("foo -s; bar -f 'quiz//'; baz");

        script.pushToShellContext(ctx);

        String line = ctx.readLine();
        assertEquals(line, "foo -s");

        line = ctx.readLine();
        assertEquals(line, "bar -f 'quiz//'");

        line = ctx.readLine();
        assertEquals(line, "baz");
    }

    public void testNonInteractiveQuit () throws Exception {
        String script = "quit";
        Core core = new Core (ctx, true, true);
        int exitCode = core.run(new Script(script));
        
        assertEquals("Issuing the script '" + script
                     + "' should return with code 0", 0, exitCode);
    }

    public void testAliases() throws Exception {
        String script = "exit";
        Core core = new Core (ctx, true, true);
        int exitCode = core.run(new Script(script));

        assertEquals("Issuing the script '" + script
                     + "' should return with code 0", 0, exitCode);
    }

    public void testNonInteractiveHelpQuit () throws Exception {
        String script = "help;quit";
        Core core = new Core (ctx, true, true);
        int exitCode = core.run(new Script(script));

        assertEquals("Issuing the script '" + script
                     + "' should return with code 0", 0, exitCode);
    }

    public void testNonInteractiveBadCommand () throws Exception {
        String script = "baaazooo -w --pretty; help; quit";
        Core core = new Core (ctx, true, true);
        int exitCode = core.run(new Script(script));

        assertEquals("Issuing the script '" + script
                     + "' should return with code -1", -1, exitCode);
    }

    public void testNonInteractiveBadSwitch () throws Exception {
        String script = "help -boogawoo; help; quit";
        Core core = new Core (ctx, true, true);
        int exitCode = core.run(new Script(script));

        assertEquals("Issuing the script '" + script
                     + "' should return with code -4", -4, exitCode);
    }

    public static void main (String[] args) {
        ShellTest test = new ShellTest();
        try {
            test.setUp();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        Core core = new Core (test.ctx, true, true);
        int exitCode = core.run(null);

        System.exit(exitCode);
    }

}




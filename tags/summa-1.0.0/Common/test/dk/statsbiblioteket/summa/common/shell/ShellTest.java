/* $Id: ShellTest.java,v 1.2 2007/10/04 13:28:21 te Exp $
 * $Revision: 1.2 $
 * $Date: 2007/10/04 13:28:21 $
 * $Author: te $
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.common.shell;

import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.summa.common.shell.notifications.SyntaxErrorNotification;
import dk.statsbiblioteket.summa.common.shell.commands.Exec;

import java.util.Arrays;
import java.util.Iterator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;

import junit.framework.TestCase;

@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public class ShellTest extends TestCase {

    Core core;
    ShellContext ctx;

    public void setUp () throws Exception {        
        ctx = new ShellContext () {
                private PushbackReader in = new PushbackReader
                                       (new InputStreamReader(System.in), 2048);
                private BufferedReader lineIn =  new BufferedReader(in, 1);

                public void error(String msg) {
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
                    try {
                        String line = lineIn.readLine().trim();
                        return line; 
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read input", e);
                    }
                }

                public void pushLine (String line) {
                    /* Make sure we do have a new line */
                    if (!line.endsWith("\n")) {
                        line = line + "\n";
                    }

                    try {
                        in.unread (line.toCharArray());
                    } catch (IOException e) {
                        throw new RuntimeException ("Failed to push line: '"
                                                    + line + "'", e);
                    }
                }

                public void prompt (String prompt) {
                    System.out.print(prompt);
                }
            };

        core = new Core (ctx, true);
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
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        sc = new Script("  foo  ;    bar");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        /* Check compact layout and trailing ; */
        sc = new Script("foo;bar;");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        /* Check newline delimiting */
        sc = new Script("foo\nbar");
        stmts = new String[] {"foo", "bar"};
        iter = sc.iterator();
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        /* Three statements and a trailing \n */
        sc = new Script("foo\nbar\nbaz\n");
        stmts = new String[] {"foo", "bar", "baz"};
        iter = sc.iterator();
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        /* Check escapes */
        sc = new Script("foo  \\;    bar");
        stmts = new String[] {"foo  ;    bar"};
        iter = sc.iterator();
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        sc = new Script("foo  \\n    bar");
        stmts = new String[] {"foo  \n    bar"};
        iter = sc.iterator();
        for (int i = 0; i < stmts.length; i++) {
            assertEquals(stmts[i], iter.next());
        }

        /* Empty scripts */
        sc = new Script("\n");
        iter = sc.iterator();
        assertFalse(iter.hasNext());

        sc = new Script(";");
        iter = sc.iterator();
        for (String s : sc)
            System.out.println ("TOK"+s);
        assertFalse(iter.hasNext());
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
        Command exec = new Exec();
        Script script = new Script ("foo -s; bar -f 'quiz//'; baz");

        script.pushToShellContext(ctx);

        String line = ctx.readLine();
        assertEquals(line, "foo -s");

        line = ctx.readLine();
        assertEquals(line, "bar -f 'quiz//'");

        line = ctx.readLine();
        assertEquals(line, "baz");
    }

    public static void main (String[] args) {
        ShellTest test = new ShellTest();
        try {
            test.setUp();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        Core core = new Core (test.ctx, true);
        core.run(args);
    }

}




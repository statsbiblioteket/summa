package dk.statsbiblioteket.summa.search.tools;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.util.Strings;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Arrays;

public class QuerySanitizerTest extends TestCase {
    private QuerySanitizer sanitizer = new QuerySanitizer(Configuration.newMemoryBased());
    private QuerySanitizer removingSanitizer = new QuerySanitizer(Configuration.newMemoryBased(
        QuerySanitizer.CONF_FIX_EXCLAMATIONS, "remove",        
        QuerySanitizer.CONF_FIX_QUALIFIERS, "remove",
        QuerySanitizer.CONF_FIX_SMARTQUOTES, "remove"
    ));
    
    private QuerySanitizer trailingQuestionMarkSanitizer = new QuerySanitizer(Configuration.newMemoryBased(               
        QuerySanitizer.CONF_FIX_TRAILING_QUESTIONMARKS, "true")
    );

    
    private QuerySanitizer.SanitizedQuery.CHANGE ERROR = QuerySanitizer.SanitizedQuery.CHANGE.error;
    private QuerySanitizer.SanitizedQuery.CHANGE SYNTAX = QuerySanitizer.SanitizedQuery.CHANGE.summasyntax;

    public QuerySanitizerTest(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public static Test suite() {
        return new TestSuite(QuerySanitizerTest.class);
    }

    public void testLowercasing() {
        final String RANGE = "mtime:[2017-01-10T00:00:00Z TO *]";
        assertSanitize("Range query",              RANGE,         RANGE, false);
    }

    public void testSmartQuotes() {
        for (char quoteSign: QuerySanitizer.DEFAULT_SMARTQUOTES.toCharArray()) {
            assertSanitize("Remove alternative quote signs " + quoteSign,
                           "foo bar", "foo " + quoteSign + "bar" + quoteSign, true, ERROR);
            assertSanitize("Replace alternative quote signs " + quoteSign,
                           "foo \"bar\"", "foo " + quoteSign + "bar" + quoteSign, false, ERROR);
        }
    }

    public void testUnbalancedQuotes() {
        assertSanitize("Unbalanced quotes",              "foo bar",         "foo bar\"", false, ERROR);
        assertSanitize("Unbalanced quotes",              "foo bar",         "\"foo bar", false, ERROR);
        assertSanitize("Unbalanced quotes",              "foo bar zoo",     "foo \"bar zoo", false, ERROR);
        assertSanitize("Balanced quotes",                "foo \"bar zoo\"", "foo \"bar zoo\"", false);
        assertSanitize("Escaped quotes",                 "foo \\\"bar zoo", "foo \\\"bar zoo", false);
        assertSanitize("Unbalanced quotes with escaped", "foo \\\"bar zoo", "\"foo \\\"bar zoo", false, ERROR);
    }

    public void testUnbalancedParentheses() {
        assertSanitize("Quoted parentheses",          "foo \"(bar zoo\"",    "foo \"(bar zoo\"", false);
        assertSanitize("Unbalanced parentheses",      "foo bar",             "foo bar)", false, ERROR);
        assertSanitize("Unbalanced parentheses",      "foo bar",             "(foo bar", false, ERROR);
        assertSanitize("Unbalanced parentheses",      "foo bar zoo",         "foo (bar zoo", false, ERROR);
        assertSanitize("Balanced parentheses",        "foo (bar zoo)",       "foo (bar zoo)", false);
        assertSanitize("Double balanced parentheses", "foo (bar (baz goo))", "foo (bar (baz goo))", false);
    }

    public void testColonWithMissingTerms() {
        assertSanitize("Bad colon1",    "foo\\: bar",   "foo: bar",      false, SYNTAX);
        assertSanitize("Bad colon2",    "foo \\: bar",  "foo : bar",     false, SYNTAX);
        assertSanitize("Bad colon3",    "foo bar\\:",   "foo bar:",      false, SYNTAX);
        assertSanitize("OK colon",      "foo:bar",      "foo:bar",       false);
        assertSanitize("Quoted colon",  "\"foo: bar\"",  "\"foo: bar\"", false);
        assertSanitize("Escaped colon", "foo\\: bar",   "foo\\: bar",    false);
    }

    public void testKeepExplicitBooleans() {
        assertSanitize("Keep the booleans",
                       "foo AND bar AND zoo:(moo OR boo)",    "foo AND bar AND zoo:(moo OR boo)", false);
//        assertSanitize("OR anywhere means explicit booleans everywhere",
//                       "foo AND bar AND zoo:(moo OR boo)",    "foo bar zoo:(moo OR boo)", false);
    }

    public void testEscaping() {
        assertEquals("Space in term", Configuration.newMemoryBased(), "foo\\ bar", "foo\\ bar");
        assertEquals("Space in term, qualified", Configuration.newMemoryBased(),
                     "zoo:foo\\ bar", "zoo:foo\\ bar");
    }

    private void assertEquals(String message, Configuration conf, String expected, String query) {
        QuerySanitizer sanitizer = new QuerySanitizer(conf);
        assertEquals(message, expected, sanitizer.sanitize(query).getLastQuery());
    }

    public void testTrailingExclamationMark() {
        assertSanitize("Bad trailing exclamation mark",     "foo bar\\!",   "foo bar!",     false, SYNTAX);
        assertSanitize("Bad trailing exclamation mark",     "foo\\! bar",   "foo! bar",     false, SYNTAX);
        assertSanitize("Bad trailing exclamation mark",     "foo!bar",      "foo!bar",      false);
        assertSanitize("Quoted trailing exclamation mark",  "\"foo bar!\"", "\"foo bar!\"", false);
        assertSanitize("Escaped trailing exclamation mark", "foo bar\\!",   "foo bar\\!",   false);
    }
    
    public void testTrailingQuestionmarks() {
      assertSanitizeQuestionmark("Bad trailing questionmark",     "Hvor er Toke*",   "Hvor er Toke?");
      assertSanitizeQuestionmark("Bad trailing questionmarks",     "Hvor er Toke*",   "Hvor er Toke????");
      assertSanitizeQuestionmark("No change",     "Hvor er Toke",   "Hvor er Toke");
      assertSanitizeQuestionmark("No change",     "Hvor ? Toke",   "Hvor ? Toke");
      assertSanitizeQuestionmark("No change",     "?Hvor er Toke",   "?Hvor er Toke");
    }
    
    
    

    public void testDash() {
        assertSanitize("Phrased words with dash",            "\"foo-bar\"", "\"foo-bar\"", true);
    }

    public void testRemoveOption() {
        assertSanitize("Bad trailing exclamation mark",     "foo bar",     "foo bar!", true, SYNTAX);
        assertSanitize("Bad trailing exclamation mark",     "foo bar",     "foo! bar", true, SYNTAX);
        assertSanitize("Bad trailing exclamation mark",     "foo!bar",     "foo!bar",  true);
        assertSanitize("Bad colon",                         "foo bar",     "foo bar:", true, SYNTAX);
    }

    public void testRegexpEscape() {
        assertSanitize("Regexp escaping", "foo\\/bar", "foo/bar", true, SYNTAX);
        assertSanitize("Regexp escaping in phrases", "\"foo/bar\"", "\"foo/bar\"", true);
    }

    public void testMixedProblems() {
        assertSanitize("Gallimaufry", "foo bar\\!",      "\"foo (bar!",     false, ERROR, ERROR, SYNTAX);
        assertSanitize("Hodgepodge",  "foo \"(\"bar\\!", "foo \"(\"bar!",   false, SYNTAX);
        assertSanitize("Potpourri",   "foo (bar\"!\")",  "foo (bar\"!\")",  false);
        assertSanitize("Salmagundi",  "foo\\!\\\" bar",  "foo\\!\\\" bar)", false, ERROR);
    }

    private void assertSanitize(String message, String expected, String query, boolean removeOffending,
                                QuerySanitizer.SanitizedQuery.CHANGE... expectedChanges) {
        QuerySanitizer.SanitizedQuery clean =
            removeOffending ? removingSanitizer.sanitize(query) : sanitizer.sanitize(query);
        assertEquals(message + ": '" + query + "'", expected, clean.getLastQuery());

        String expectedC = Strings.join(Arrays.asList(expectedChanges), ", ");
        String actualC = Strings.join(clean.getChanges(), ", ");
        assertEquals(message + ": '" + query + "'. Change type", expectedC, actualC);
    }
    
    private void assertSanitizeQuestionmark(String message, String expected, String query) {
       String result = trailingQuestionMarkSanitizer.sanitize(query).getLastQuery();
       assertEquals(message + ": '" + query + "'", expected, result);
}
    
}

package dk.statsbiblioteket.summa.support.harmonise;

import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.util.FlexiblePair;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetKeys;
import dk.statsbiblioteket.summa.facetbrowser.api.FacetResultExternal;
import dk.statsbiblioteket.summa.search.api.Request;
import dk.statsbiblioteket.summa.search.api.ResponseCollection;
import dk.statsbiblioteket.summa.search.api.document.DocumentKeys;
import dk.statsbiblioteket.util.Strings;
import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class InteractionAdjusterTest extends TestCase {
    public InteractionAdjusterTest(String name) {
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
        return new TestSuite(InteractionAdjusterTest.class);
    }

    public void testFacetFieldQueryRewrite() {
        InteractionAdjuster adjuster = createAdjuster();
        Request request = new Request(
            FacetKeys.SEARCH_FACET_FACETS, "llang, lma_long"
        );
        Request rewritten = adjuster.rewrite(request);
        assertEquals("The rewritten facet fields should be as expected",
                     "Language, ContentType",
                     Strings.join(rewritten.getStrings(
                         FacetKeys.SEARCH_FACET_FACETS), ", "));
    }

    // ContentType -> lma_long
    public void testFacetFieldResultRewrite() {
        InteractionAdjuster adjuster = createAdjuster();

        Request request = new Request(
            FacetKeys.SEARCH_FACET_FACETS, "lma_long"
        );

        HashMap<String, Integer> facetIDs = new HashMap<String, Integer>();
        facetIDs.put("ContentType", 0);
        HashMap<String, String[]> fields = new HashMap<String, String[]>();
        fields.put("ContentType", new String[]{"ContentType"});
        FacetResultExternal summaFacetResult = new FacetResultExternal(
            new HashMap<String, Integer>(), facetIDs, fields);
        //noinspection unchecked
        summaFacetResult.getMap().put("ContentType", Arrays.asList(
            new FlexiblePair<String, Integer>(
                "Audio Sound", 87, FlexiblePair.SortType.PRIMARY_ASCENDING)));
        ResponseCollection responses = new ResponseCollection();
        responses.add(summaFacetResult);

        adjuster.adjust(request, responses);

        assertEquals("The adjusted response should contain rewritten facet "
                     + "query field for material type",
                     "lma_long",
                     ((FacetResultExternal)responses.iterator().next()).
                         getFields().entrySet().iterator().next().
                         getValue()[0]);
        assertEquals("The adjusted response should contain rewritten field for"
                     + " material type",
                     "lma_long",
                     ((FacetResultExternal)responses.iterator().next()).
                         getMap().entrySet().iterator().next().getKey());
        assertEquals("The adjusted response should contain rewritten tag for"
                     + " material type",
                     "audio",
                     ((FacetResultExternal)responses.iterator().next()).
                         getMap().entrySet().iterator().next().getValue().
                         get(0).getKey());
    }

    private InteractionAdjuster createAdjuster() {
        Configuration conf = Configuration.newMemoryBased(
            InteractionAdjuster.CONF_IDENTIFIER, "tester",
            InteractionAdjuster.CONF_ADJUST_FACET_FIELDS,
            "author_normalised - Author, lma_long - ContentType, "
            + "llang - Language, lsubject - SubjectTerms",
            InteractionAdjuster.CONF_ADJUST_DOCUMENT_FIELDS,
            "recordID - ID, author_normalised - Author, "
            + "lma_long - ContentType, llang - Language, "
            + "lsubject - SubjectTerms"
        );
        List<Configuration> tags;
        try {
            tags = conf.createSubConfigurations(
                InteractionAdjuster.CONF_ADJUST_FACET_TAGS, 2);
        } catch (IOException e) {
            throw new RuntimeException("Configuration creation failed", e);
        }
        tags.get(0).set(TagAdjuster.CONF_FACET_NAME, "llang");
        tags.get(0).set(TagAdjuster.CONF_TAG_MAP,
                        "English - eng, "
                        + "MyLang - one;two, "
                        + "Moo;Doo - single, "
                        + "Boom - boo, "
                        + "Boom - hoo, "
                        + "Bim;Bam - bi;ba");
        tags.get(1).set(TagAdjuster.CONF_FACET_NAME, "lma_long");
        tags.get(1).set(TagAdjuster.CONF_TAG_MAP, "Audio Sound - audio");
        return new InteractionAdjuster(conf);
    }

    public void testQueryFieldRewrite() {
        InteractionAdjuster adjuster = createAdjuster();
        assertAdjustment(adjuster, "(+Language:foo +bar)", "llang:\"foo\" bar");
    }

    public void testQueryTagRewrite_nonAdjusting() {
        InteractionAdjuster adjuster = createAdjuster();
        assertAdjustment(adjuster, "(+Language:foo +bar)", "llang:\"foo\" bar");
        assertAdjustment(adjuster, "English", "English");
        assertAdjustment(adjuster, "eng", "eng");
        assertAdjustment(adjuster, "ContentType:eng", "lma_long:eng");
    }

    // "llang:(foo OR bar)"?
    public void testQueryTagRewrite_1to1() {
        InteractionAdjuster adjuster = createAdjuster();
        assertAdjustment(adjuster, "Language:English", "llang:eng");
        assertAdjustment(adjuster, "Language:English", "llang:\"eng\"");
        assertAdjustment(adjuster, "Language:MyLang", "llang:\"one\"");
        assertAdjustment(adjuster, "Language:MyLang", "llang:\"two\"");
    }

    public void testQueryTagRewrite_1ton() {
        InteractionAdjuster adjuster = createAdjuster();
        assertAdjustment(adjuster, "Language:MyLang", "llang:\"one\"");
        assertAdjustment(adjuster, "Language:MyLang", "llang:\"two\"");
        assertAdjustment(adjuster, "Language:Boom", "llang:boo");
        assertAdjustment(adjuster, "Language:Boom", "llang:hoo");
    }

    public void testQueryTagRewrite_nto1() {
        InteractionAdjuster adjuster = createAdjuster();
        assertAdjustment(adjuster,
                         "(Language:Moo OR Language:Doo)", "llang:\"single\"");
    }

    public void testQueryTagRewrite_nton() {
        InteractionAdjuster adjuster = createAdjuster();
        assertAdjustment(adjuster,
                         "(Language:Bim OR Language:Bam)", "llang:\"bi\"");
        assertAdjustment(adjuster,
                         "(Language:Bim OR Language:Bam)", "llang:\"ba\"");
    }

    private void assertAdjustment(
        InteractionAdjuster adjuster, String expected, String query) {
        Request request = new Request(DocumentKeys.SEARCH_QUERY, query);
        Request rewritten = adjuster.rewrite(request);
        assertEquals("The query should be rewritten correctly",
                     expected,
                     rewritten.get(DocumentKeys.SEARCH_QUERY));
    }
}

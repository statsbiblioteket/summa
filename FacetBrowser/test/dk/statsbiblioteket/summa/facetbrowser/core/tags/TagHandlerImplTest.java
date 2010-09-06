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
package dk.statsbiblioteket.summa.facetbrowser.core.tags;

import dk.statsbiblioteket.summa.facetbrowser.BaseObjects;
import dk.statsbiblioteket.summa.facetbrowser.FacetStructure;
import dk.statsbiblioteket.summa.facetbrowser.Structure;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TagHandlerImpl Tester.
 *
 * @author <Authors name>
 * @since <pre>09/03/2008</pre>
 * @version 1.0
 */
@SuppressWarnings({"DuplicateStringLiteralInspection"})
public class TagHandlerImplTest extends TestCase {
    private Log log = LogFactory.getLog(TagHandlerImplTest.class);

    public TagHandlerImplTest(String name) {
        super(name);
    }

    BaseObjects bo;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        bo = new BaseObjects();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        bo.close();
    }

    public void testGetFacets() throws Exception {
        TagHandler tagHandler = bo.getTagHandler();
        List<String> thFacetNames =
                new ArrayList<String>(tagHandler.getFacets().size());
        for (Facet thFacet: tagHandler.getFacets()) {
            thFacetNames.add(thFacet.getName());
        }
        assertTrue("The facet-names from TahHandler should be as expected",
                   Arrays.equals(
                          thFacetNames.toArray(new String[thFacetNames.size()]),
                          bo.getFacetNames().toArray(
                                  new String[bo.getFacetNames().size()])));
    }

    public void testPlainAdd() throws Exception {
        TagHandler tagHandler = bo.getTagHandler();
        Structure structure = bo.getStructure();
        FacetStructure facet =
                structure.getFacet(structure.getFacetNames().get(0));
        int facetID = facet.getFacetID();
        tagHandler.insertTag(facetID, "A");
        tagHandler.insertTag(facetID, "A");
        tagHandler.insertTag(facetID, "B");
        assertEquals("The facet-count should be as expected",
                     2, tagHandler.getTagCount(facet.getName()));
    }

    public void testAddAndRemove() throws Exception {
        TagHandler tagHandler = bo.getTagHandler();
        Structure structure = bo.getStructure();
        FacetStructure facet =
                structure.getFacet(structure.getFacetNames().get(0));
        int facetID = facet.getFacetID();
        tagHandler.insertTag(facetID, "A");
        tagHandler.insertTag(facetID, "A");
        tagHandler.insertTag(facetID, "B");
        assertEquals("The facet-count should be as expected after add",
                     2, tagHandler.getTagCount(facet.getName()));
        tagHandler.removeTag(facetID, tagHandler.getTagID(facetID, "A"));
        assertEquals("The facet-count should be as expected after delete",
                     1, tagHandler.getTagCount(facet.getName()));
    }

    public void testPersistence() throws Exception {
        TagHandler tagHandler = bo.getMemoryTagHandler(1);
        tagHandler.clearTags();
        Structure structure = bo.getStructure();
        FacetStructure facet =
                structure.getFacet(structure.getFacetNames().get(0));
        int facetID = facet.getFacetID();
        tagHandler.insertTag(facetID, "A");
        tagHandler.insertTag(facetID, "B");
        tagHandler.store();
        tagHandler.close();

        log.debug("Loading new taghandler structure");
        TagHandler tagHandler2 = bo.getMemoryTagHandler(1);
        assertEquals("The facet-count should be as expected after open",
                     2, tagHandler2.getTagCount(facet.getName()));
        tagHandler2.close();
    }

    public static Test suite() {
        return new TestSuite(TagHandlerImplTest.class);
    }
}
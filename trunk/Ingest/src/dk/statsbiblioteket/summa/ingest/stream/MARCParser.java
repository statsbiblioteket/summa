/* $Id$
 *
 * The Summa project.
 * Copyright (C) 2005-2008  The State and University Library
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
package dk.statsbiblioteket.summa.ingest.stream;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.text.ParseException;
import java.io.StringWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import dk.statsbiblioteket.util.qa.QAInfo;
import dk.statsbiblioteket.summa.common.Record;
import dk.statsbiblioteket.summa.common.util.ParseUtil;
import dk.statsbiblioteket.summa.common.configuration.Configuration;

/**
 * Takes a Stream as input, splits into MARC-records and creates Summa-Records
 * with the content. As part of the split, the following properties are
 * extracted from the MARC-record:
 * <ul>
 *   <li>recordID</li>
 *   <li>state (deleted or not)</li>
 *   <li>parent/child relations</li>
 * </ul>
 * </p><p>
 * The extraction of recordID and parent/child-relations is non-trivial due to
 * the nature of MARC, so the input is parsed. A streaming parser is used as
 * performance is prioritized over clarity (the streaming parser has less
 * GC overhead than a full DOM build).
 * </p><p>
 * The overall structure of a MARC-dump is
 * {@code
<collection xmlns="http://www.loc.gov/MARC21/slim">
<record>
  <leader>...</leader>
  <datafield tag="..." ind1="..." ind2="...">
    <subfield code="...">...</subfield>+
  </datafield>*
</record>*
</collection>
} where * indicates multiple and + at least one occurence of the element.
 * </p><p>
 * There are different variants of MARC, but the structure is always the same
 * (well, at least we think so - MARC is a fixed target).
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "te")
public abstract class MARCParser extends ThreadedStreamParser {
    private static Log log = LogFactory.getLog(MARCParser.class);

    /**
     * The base for the Records generated by the parser.
     * </p><p>
     * This property is mandatory.
     */
    public static final String CONF_BASE = "summa.ingest.marcparser.base";

    @SuppressWarnings({"DuplicateStringLiteralInspection"})
    public static final String MARC_TAG_RECORD = "record";
    public static final String MARC_TAG_LEADER = "leader";
    public static final String MARC_TAG_DATAFIELD = "datafield";
    public static final String MARC_TAG_DATAFIELD_ATTRIBUTE_TAG = "tag";
    public static final String MARC_TAG_DATAFIELD_ATTRIBUTE_IND1 = "ind1";
    public static final String MARC_TAG_DATAFIELD_ATTRIBUTE_IND2 = "ind2";
    public static final String MARC_TAG_SUBFIELD = "subfield";
    public static final String MARC_TAG_SUBFIELD_ATTRIBUTE_CODE = "code";

    private XMLInputFactory inputFactory;

    /**
     * The base for Records produced by implementations of MARCParser.
     */
    protected String base;

    public MARCParser(Configuration conf) {
        super(conf);
        inputFactory = XMLInputFactory.newInstance();
        // There should be no CData in the MARC XML
//        inputFactory.setProperty("report-cdata-event", Boolean.TRUE);
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        base = conf.getString(CONF_BASE, null);
        if (base == null) {
            throw new ConfigurationException(String.format(
                    "A base must be specified with key %s", CONF_BASE));
        }
    }

    // http://java.sun.com/javaee/5/docs/tutorial/doc/bnbfl.html
    protected void protectedRun() throws Exception {
        XMLStreamReader reader = inputFactory.createXMLStreamReader(
                sourcePayload.getStream(), "utf-8");
        // Positioned at startDocument
        int eventType = reader.getEventType();
        if (eventType != XMLEvent.START_ELEMENT) {
            throw new ParseException("The first element was not start", 0);
        }

        while (running && reader.hasNext()) {
            eventType = reader.next();
            //noinspection DuplicateStringLiteralInspection
            if (eventType == XMLEvent.START_ELEMENT
                && MARC_TAG_RECORD.equals(reader.getLocalName())) {
                processInRecord(reader);
            }
        }
    }

    /**
     * Collect record information and ultimately build a Record and add it to
     * the queue before returning.
     * @param reader a reader positioned at the start of a record.
     * @throws XMLStreamException   if a parse error occured.
     * @throws InterruptedException if the process was interrupted while adding
     *                              to the queue.
     */
    private void processInRecord(XMLStreamReader reader) throws
                                                         XMLStreamException,
                                                         InterruptedException {
        initializeNewParse();
        StringWriter content = new StringWriter(2000); // Full MARC content

        // TODO: Add XML-declaration and namespace
        content.append(beginTagToString(reader));
        while (running && reader.hasNext()) {
            int eventType = reader.next();

            switch(eventType) {
                case XMLEvent.START_ELEMENT :
                    content.append(beginTagToString(reader));
                    if (MARC_TAG_DATAFIELD.equals(reader.getLocalName())) {
                        processDataField(reader, content);
                    } else if (MARC_TAG_LEADER.equals(reader.getLocalName())) {
                        processLeader(reader, content);
                    } else {
                        log.warn(String.format(
                                "Unexpected start-tag '%s' while parsing MARC "
                                + "for %s",
                                reader.getLocalName(), sourcePayload));
                    }
                    break;
                case XMLEvent.END_ELEMENT :
                    content.append(endTagToString(reader));
                    if (MARC_TAG_RECORD.equals(reader.getLocalName())) {
                        if (running) {
                            Record record = makeRecord(content.toString());
                            if (record != null) {
                                queue.put(record);
                            }
                        }
                        return;
                    }
                    log.warn(String.format(
                            "Unexpected end-tag '%s' while parsing MARC for %s",
                            reader.getLocalName(), sourcePayload));
                    break;
                case XMLEvent.CHARACTERS :
                    log.warn(String.format(
                            "Unexpected text '%s' while parsing MARC for %s",
                            reader.getText(), sourcePayload));
                    // TODO: Test for "foo &lt;bar"
                    content.append(reader.getText());
                    break;
                default:
                    log.warn(String.format(
                            "Unexpended event %s while processing %s",
                             eventID2String(eventType), sourcePayload));
            }
        }
        if (!running) {
            log.debug("processInRecord stopped as running was false");
        }
    }

    /**
     * Process the leader in a MARC record, such as
     * {@code <leader>.....cmm  22.....0  45032</leader>}.
     * @param reader  the reader, positioned at the start-tag for a leader.
     * @param content the XML content up till this point, including <leader>.
     * @throws XMLStreamException   if a parse error occured.
     * @throws InterruptedException if the process was interrupted while adding
     *                              to the queue.
     */
    private void processLeader(XMLStreamReader reader, StringWriter content)
            throws XMLStreamException, InterruptedException {
        log.trace("Reached leader start-tag");
        String leaderContent = "";
        while (running && reader.hasNext()) {
            int eventType = reader.next();

            switch(eventType) {
                case XMLEvent.START_ELEMENT :
                    content.append(beginTagToString(reader));
                    log.warn(String.format(
                            "processLeader: Reached unexpected start-tag "
                            + "<%s> for %s. Expected none",
                            reader.getLocalName(), sourcePayload));
                    break;
                case XMLEvent.END_ELEMENT :
                    content.append(endTagToString(reader));
                    if (MARC_TAG_LEADER.equals(reader.getLocalName())) {
                        if ("".equals(leaderContent)) {
                            log.debug("No leader content for " + sourcePayload);
                        }
                        setLeader(leaderContent);
                        return;
                    }
                    log.warn(String.format(
                            "processLeader: Reached unexpected end-tag </%s> "
                            + "for %s. Expected </%s>",
                            reader.getLocalName(), sourcePayload,
                            MARC_TAG_LEADER));
                    break;
                case XMLEvent.CHARACTERS :
                    // TODO: Test for "foo &lt;bar"
                    leaderContent = reader.getText();
                    content.append(reader.getText());
                    break;
                default:
                    log.warn(String.format(
                            "processLeader: Unexpended event %s while "
                            + "processing %s",
                             eventID2String(eventType), sourcePayload));
            }
        }
    }

    /**
     * Process datafield-elements in a MARC record, such as {@code
    <datafield tag="..." ind1="..." ind2="...">
      <subfield code="...">...</subfield>+
    </datafield>
     }.
     * @param reader  the reader, positioned at the start-tag for a datafield.
     * @param content the XML content up till this point, including
     *                {@code <datafield ...>}.
     * @throws XMLStreamException   if a parse error occured.
     */
    protected void processDataField(XMLStreamReader reader,
                                      StringWriter content)
                                                     throws XMLStreamException {
        log.trace("Reached datafield start-tag");
        String tag = null;
        String ind1 = null;
        String ind2 = null;
        for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
            if (reader.getAttributeLocalName(i).equals(
                    MARC_TAG_DATAFIELD_ATTRIBUTE_TAG)) {
                tag = reader.getAttributeValue(i);
            } else if (reader.getAttributeLocalName(i).equals(
                    MARC_TAG_DATAFIELD_ATTRIBUTE_IND1)) {
                ind1 = reader.getAttributeValue(i);
            } else if (reader.getAttributeLocalName(i).equals(
                    MARC_TAG_DATAFIELD_ATTRIBUTE_IND2)) {
                ind2 = reader.getAttributeValue(i);
            } else {
                log.warn(String.format(
                        "processDatafield: Unexpected attribute %s with value" +
                                " '%s' for tag %s",
                        reader.getAttributeLocalName(i),
                        reader.getAttributeValue(i), MARC_TAG_DATAFIELD));
            }
        }
        beginDataField(tag, ind1, ind2);

        while (running && reader.hasNext()) {
            int eventType = reader.next();

            switch(eventType) {
                case XMLEvent.START_ELEMENT :
                    content.append(beginTagToString(reader));
                    if (MARC_TAG_SUBFIELD.equals(reader.getLocalName())) {
                        processSubField(reader, content, tag, ind1, ind2);
                    } else {
                        log.warn(String.format(
                                "processDataField: Reached unexpected start-tag"
                                        + " <%s> for %s. Expected %s",
                                reader.getLocalName(), sourcePayload,
                                MARC_TAG_SUBFIELD));
                    }
                    break;
                case XMLEvent.END_ELEMENT :
                    content.append(endTagToString(reader));
                    if (MARC_TAG_DATAFIELD.equals(reader.getLocalName())) {
                        endDataField(tag);
                        return;
                    }
                    log.warn(String.format(
                            "processDataField: Reached unexpected end-tag </%s>"
                            + " for %s. Expected </%s>",
                            reader.getLocalName(), sourcePayload,
                            MARC_TAG_DATAFIELD));
                    break;
                case XMLEvent.CHARACTERS :
                    log.warn(String.format(
                            "processDatafield: Unexpected text '%s' while "
                                    + "parsing MARC for %s",
                            reader.getText(), sourcePayload));
                    content.append(reader.getText());
                    break;
                default:
                    log.warn(String.format(
                            "processDataField: Unexpended event %s while "
                            + "processing %s",
                             eventID2String(eventType), sourcePayload));
            }
        }
    }

    protected void processSubField(XMLStreamReader reader, StringWriter content,
                                   String tag, String ind1, String ind2) throws
                                                            XMLStreamException {
        log.trace("Reached subfield start-tag");

        String code = null;
        for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
            if (reader.getAttributeLocalName(i).equals(
                    MARC_TAG_DATAFIELD_ATTRIBUTE_TAG)) {
                code = reader.getAttributeValue(i);
            } else {
                log.warn(String.format(
                        "processSubfield: Unexpected attribute %s with value '"
                                + "%s' for tag %s",
                        reader.getAttributeLocalName(i),
                        reader.getAttributeValue(i), MARC_TAG_SUBFIELD));
            }
        }

        String subfieldcontent = "";
        while (running && reader.hasNext()) {
            int eventType = reader.next();

            switch(eventType) {
                case XMLEvent.START_ELEMENT :
                    content.append(beginTagToString(reader));
                    log.warn(String.format(
                            "processSubField: Reached unexpected start-tag "
                            + "<%s> for %s. Expected none",
                            reader.getLocalName(), sourcePayload));
                    break;
                case XMLEvent.END_ELEMENT :
                    content.append(endTagToString(reader));
                    if (MARC_TAG_SUBFIELD.equals(reader.getLocalName())) {
                        if ("".equals(subfieldcontent)) {
                            log.debug("No subfield content for "
                                    + sourcePayload + " in datafield " + tag
                                    + ", subfield " + code);
                        }
                        setSubField(tag, ind1, ind2, code, subfieldcontent);
                        return;
                    }
                    log.warn(String.format(
                            "processsubField: Reached unexpected end-tag </%s> "
                            + "for %s. Expected </%s>",
                            reader.getLocalName(), sourcePayload,
                            MARC_TAG_SUBFIELD));
                    break;
                case XMLEvent.CHARACTERS :
                    subfieldcontent = reader.getText();
                    content.append(reader.getText());
                    break;
                default:
                    log.warn(String.format(
                            "processSubField: Unexpended event %s while "
                            + "processing %s",
                             eventID2String(eventType), sourcePayload));
            }
        }
    }

    /**
     * Initialize the MARC parser. This normally involves resetting all
     * attributes to their default states. This method is called at the start
     * of all record-elements.
     */
    protected abstract void initializeNewParse();

    /**
     * Set the leader for the record.
     * @param content the text in the leader-element.
     */
    protected abstract void setLeader(String content);

    /**
     * Marks the beginning of a datafield-element. Note that the method
     * {@link #setSubField} also contains tag, ind1 and ind2, so the
     * beginDataField-method might not need do anything, depending on
     * implementation.
     * @param tag  the id for the datafield.
     * @param ind1 the ind1-attribute for the datafield.
     * @param ind2 the ind2-attribute for the datafield.
     */
    protected abstract void beginDataField(String tag,
                                           String ind1, String ind2);

    /**
     * Set a subfield for a datafield. It is guaranteed that the subfields
     * for a datafield will follow after {@link #beginDataField} and before
     * {@link #endDataField}.
     * @param dataFieldTag  the id for the datafield.
     * @param dataFieldInd1 the ind-1 attribute for the datafield.
     * @param dataFieldInd2 the ind-2 attribute for the datafield.
     * @param subFieldCode  the code for the subfield.
     * @param subFieldContent the content of the subfield.
     */
    protected abstract void setSubField(String dataFieldTag,
                                        String dataFieldInd1,
                                        String dataFieldInd2,
                                        String subFieldCode,
                                        String subFieldContent);

    /**
     * Marks the end a datafield-element.
     * @param tag the id for the datafield.
     */
    protected abstract void endDataField(String tag);

    /**
     * Create a Record based on the received data, if possible.
     * @param xml the XML for the MARC record, ready for insertion in a Record.
     * @return a Record based on received data or null if a Record cannot be
     *         created.
     */
    protected abstract Record makeRecord(String xml);

    /**
     * Convert a begin-tag to String. Suitable for dumping while parsing.
     * @param reader a reader pointing to a begin-tag.
     * @return the begin-tag as text.
     */
    protected String beginTagToString(XMLStreamReader reader) {
        StringWriter tag = new StringWriter(50);
        tag.append("<").append(reader.getLocalName());
        for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
            tag.append(" ").append(reader.getAttributeLocalName(i));
            tag.append("=\"");
            tag.append(ParseUtil.encode(reader.getAttributeValue(i)));
            tag.append("\"");
        }
        tag.append(">");
        return tag.toString();
    }

    /**
     * Convert an end-tag to String. Suitable for dumping while parsing.
     * @param reader a reader pointing to an end-tag.
     * @return the end-tag as text.
     */
    protected String endTagToString(XMLStreamReader reader) {
        return "</" + reader.getLocalName() + ">\n";
    }

    /**
     * Udef for debugging. Converts an XMLEvent-id to String.
     * @param eventType the event id.
     * @return the event as human redable String.
     */
    protected String eventID2String(int eventType) {
        switch (eventType) {
            case XMLEvent.START_ELEMENT:  return "START_ELEMENT";
            case XMLEvent.END_ELEMENT:    return "END_ELEMENT";
            case XMLEvent.PROCESSING_INSTRUCTION:
                return "PROCESSING_INSTRUCTION";
            case XMLEvent.CHARACTERS: return "CHARACTERS";
            case XMLEvent.COMMENT: return "COMMENT";
            case XMLEvent.START_DOCUMENT: return "START_DOCUMENT";
            case XMLEvent.END_DOCUMENT: return "END_DOCUMENT";
            case XMLEvent.ENTITY_REFERENCE: return "ENTITY_REFERENCE";
            case XMLEvent.ATTRIBUTE: return "ATTRIBUTE";
            case XMLEvent.DTD: return "DTD";
            case XMLEvent.CDATA: return "CDATA";
            case XMLEvent.SPACE: return "SPACE";
            default: return "UNKNOWN_EVENT_TYPE " + "," + eventType;
        }
    }
}

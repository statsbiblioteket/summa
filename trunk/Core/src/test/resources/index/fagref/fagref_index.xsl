<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:Index="http://statsbiblioteket.dk/summa/2008/Document"
                xmlns:xs="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:xalan="http://xml.apache.org/xalan"
                xmlns:java="http://xml.apache.org/xalan/java"
                exclude-result-prefixes="java xs xalan xsl" version="1.0">
    <xsl:include href="fagref_short_format.xsl"/>
    <xsl:include href="fagref_author.xsl"/>
    <xsl:include href="fagref_title.xsl"/>
    <xsl:include href="fagref_subject.xsl"/>
    <xsl:include href="fagref_notes.xsl"/>
    <xsl:output version="1.0" encoding="UTF-8" indent="yes" method="xml"/>
    <xsl:template match="/">
        <Index:SummaDocument version="1.0" Index:boost="5" Index:disabled_resolver="fagref">
            <xsl:attribute name="Index:id">
                <xsl:value-of select="fagref/email"/>
            </xsl:attribute>
            <xsl:for-each select="fagref">
                <Index:fields>
                    <xsl:call-template name="shortformat"/>
                    <xsl:call-template name="author"/>
                    <xsl:call-template name="title"/>
                    <xsl:call-template name="subject"/>
                    <xsl:call-template name="notes"/>
                    <Index:field Index:name="ma_long">person</Index:field>
                    <Index:field Index:name="ma_long">fagspecialist</Index:field>
                    <!--    erstatttet af recordBase-->
                    <!--  <Index:field  Index:name="ltarget"   >
                      <xsl:text>fagspecialist</xsl:text>
                      </Index:field>-->
                    <Index:field Index:name="lma_long">fagspecialist</Index:field>
                </Index:fields>
            </xsl:for-each>
        </Index:SummaDocument>
    </xsl:template>
</xsl:stylesheet>

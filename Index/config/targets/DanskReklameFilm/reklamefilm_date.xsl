<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:Index="http://statsbiblioteket.dk/2004/Index"
		xmlns:xs="http://www.w3.org/2001/XMLSchema-instance"
		xmlns:xalan="http://xml.apache.org/xalan"
		xmlns:java="http://xml.apache.org/xalan/java"
		exclude-result-prefixes="java xs xalan xsl" version="1.0">
    
	<xsl:output version="1.0" encoding="UTF-8" indent="yes" method="xml"/>
	<xsl:template name="date">

        <Index:field Index:repeat="true" Index:name="py" Index:navn="år"  Index:type="token" Index:boostFactor="2">
             <xsl:value-of select="censor"/>
        </Index:field>
        <Index:field Index:repeat="true" Index:name="py" Index:navn="år"  Index:type="token" Index:boostFactor="2">
             <xsl:value-of select="premiereDate"/>
        </Index:field>
		

    </xsl:template>

</xsl:stylesheet>
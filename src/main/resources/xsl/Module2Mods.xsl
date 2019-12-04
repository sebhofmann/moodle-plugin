<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xlink="http://www.w3.org/1999/xlink" version="3.0"
                xmlns:xsL="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="WebApplicationBaseURL"/>
  <xsl:param name="moduleID"/>

  <xsl:mode on-no-match="deep-skip"/>

  <xsl:variable name="categories"
                select="document('moodle:resolveCourseCategories')"/>


  <xsl:template match="/">
    <xsl:apply-templates select=".//SINGLE[KEY[@name='id' and VALUE/text()=$moduleID]]"/>
  </xsl:template>

  <xsl:template match="SINGLE">
    <mods:mods>
      <mods:genre valueURI="https://duepublico.uni-due.de/api/v1/classifications/mir_genres#teaching_material"
                  authorityURI="https://duepublico.uni-due.de/api/v1/classifications/mir_genres" type="intern"/>
      <xsl:apply-templates select="KEY"/>
      <xsl:for-each select="distinct-values(KEY[@name='modules']//KEY[@name='author']/VALUE/text())">
        <mods:name type="personal" xlink:type="simple">
          <mods:displayForm>
            <xsl:value-of select="."/>
          </mods:displayForm>
          <mods:role>
            <mods:roleTerm authority="marcrelator" type="code">aut</mods:roleTerm>
          </mods:role>
        </mods:name>
      </xsl:for-each>
    </mods:mods>
  </xsl:template>

  <xsl:template match="*">
  </xsl:template>

  <xsl:template match="KEY[@name='categoryid' and count(VALUE/node())&gt;0]">
    <xsl:variable name="categoryid" select="VALUE/text()"/>
    <mods:subject xlink:type="simple">
      <mods:topic><xsl:value-of select="$categories/RESPONSE/MULTIPLE/SINGLE[KEY[@name='id' and VALUE/text()=$categoryid]]/KEY[@name='name']/VALUE/text()" /></mods:topic>
    </mods:subject>
  </xsl:template>

  <xsl:template match="KEY[@name='name' and count(VALUE/node())&gt;0]">
    <mods:titleInfo xml:lang="de" xlink:type="simple">
      <xsl:choose>
        <xsl:when test="contains(VALUE/text(),':')">
          <mods:title>
            <xsl:value-of select="substring-before(VALUE/text(), ':')"/>
          </mods:title>
          <mods:subTitle>
            <xsl:value-of select="substring-after(VALUE/text(), ':')"/>
          </mods:subTitle>
        </xsl:when>
        <xsl:otherwise>
          <mods:title>
            <xsl:value-of select="VALUE/text()"/>
          </mods:title>
        </xsl:otherwise>
      </xsl:choose>
    </mods:titleInfo>
  </xsl:template>

  <!--<xsl:template match="KEY[@name='startdate' and count(VALUE/node())&gt;0]">
    <mods:originInfo eventType="publication">
      <mods:dateIssued encoding="w3cdtf">
        <xsl:variable name="date">
          <xsl:value-of
              select="(xs:dateTime('1970-01-01T00:00:00') + (VALUE/text() *1000)* xs:dayTimeDuration('PT0.001S'))"/>
          <xsl:text></xsl:text>
        </xsl:variable>
        <xsl:value-of select="substring-before($date,'T')"/>
      </mods:dateIssued>
    </mods:originInfo>
  </xsl:template>-->

  <xsl:template match="KEY[@name='summary' and count(VALUE/node())&gt;0]">
    <mods:abstract xml:lang="de" xlink:type="simple">
      <xsl:value-of select="VALUE/text()"/>
    </mods:abstract>
  </xsl:template>

</xsl:stylesheet>
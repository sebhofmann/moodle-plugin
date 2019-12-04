<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="3.0">
  <xsl:param name="WebApplicationBaseURL"/>

  <xsl:variable name="translations" select="document('translate:mir.moodle.')"/>

  <xsl:template match="MoodleCall">
    <site>
      <xsl:choose>
        <xsl:when test="@method='core_course_get_courses'">
          <xsl:call-template name="importCourse"/>
        </xsl:when>
        <xsl:when test="@method='core_enrol_get_users_courses'">
          <xsl:call-template name="listCourses"/>
        </xsl:when>
        <xsl:when test="@method='no_user'">
          <xsl:call-template name="userMissing"/>
        </xsl:when>
        <xsl:when test="@method='importResult'">

          <div class="card">
            <div class="card-header">
              <h2 class="card-title">
                <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.import.success']/text()"/>
              </h2>
            </div>
            <div class="card-body">
              <p>
                <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.import.message']/text()"/>
              </p>
            </div>
            <ul class="list-group list-group-flush">
              <xsl:for-each select="object">
                <li class="list-group-item">
                  <a href="{$WebApplicationBaseURL}receive/{@id}"><xsl:value-of select="@id"/></a>
                </li>
              </xsl:for-each>
            </ul>
          </div>
        </xsl:when>
      </xsl:choose>
    </site>
  </xsl:template>

  <!-- #### Course no user linked error ############################################################################ -->
  <xsl:template name="userMissing">
    <div class="card">
      <div class="card-header">
        <h2 class="card-title">
          <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.user.missing.title']/text()"/>
        </h2>
      </div>
      <div class="card-body">
        <p>
          <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.user.missing.message']/text()"/>
        </p>
      </div>
    </div>
  </xsl:template>

  <!-- #### Course list ############################################################################################ -->
  <xsl:template name="listCourses">
    <div class="card">
      <div class="card-header">
        <h2>
          <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.choose.course.title']/text()"/>
        </h2>
      </div>
      <div class="card-body">
        <p>
          <xsl:value-of
              select="$translations/translations/translation[@key='mir.moodle.choose.course.description']/text()"/>
        </p>
      </div>
      <ul class="list-group list-group-flush">
        <!-- TODO: filter existing courses -->
        <xsl:apply-templates select="RESPONSE/MULTIPLE/SINGLE"/>
      </ul>
    </div>
  </xsl:template>

  <xsl:template match="MoodleCall[@method='core_enrol_get_users_courses']/RESPONSE/MULTIPLE/SINGLE">

    <li class="list-group-item">
      <a href="{$WebApplicationBaseURL}servlets/MoodleServlet?importID={KEY[@name='id']/VALUE/text()}">
        <xsl:value-of select="KEY[@name='fullname']/VALUE/text()"/>
      </a>
    </li>
  </xsl:template>

  <!-- #### Course import select contents ########################################################################## -->
  <xsl:template name="importCourse">
    <xsl:variable name="courseID" select="RESPONSE/MULTIPLE/SINGLE/KEY[@name='id']/VALUE/text()"/>
    <xsl:variable name="courseContent" select="document(concat('moodle:resolveCourseContent:', $courseID))"/>
    <div class="card">
      <div class="card-header">
        <h2>
          <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.import.course.title']/text()"/>
        </h2>
      </div>
      <div class="card-body">
        <p>
          <xsl:value-of
              select="$translations/translations/translation[@key='mir.moodle.import.course.description']/text()"/>
        </p>
        <form method="post" action="{$WebApplicationBaseURL}servlets/MoodleServlet">
          <input type="hidden" name="importID" value="{$courseID}"/>
          <xsl:variable name="courseTitle" select="RESPONSE/MULTIPLE/SINGLE/KEY[@name='fullname']/VALUE/text()"/>

          <div class="form-check">
            <input class="form-check-input" type="checkbox" name="course" id="course_{$courseID}"
                   value="{$courseID}" checked="true"/>
            <label class="form-check-label" for="course_{$courseID}">
              <xsl:value-of select="$courseTitle"/>
            </label>
          </div>

          <xsl:for-each select="$courseContent/RESPONSE/MULTIPLE/SINGLE">
            <xsl:call-template name="printModule"/>
          </xsl:for-each>

          <button type="submit" class="btn btn-primary float-right">
            <xsl:value-of select="$translations/translations/translation[@key='mir.moodle.import.submit']/text()"/>
          </button>
        </form>
      </div>
    </div>

  </xsl:template>

  <xsl:template name="printModule">
    <xsl:variable name="supported" select=".//KEY[@name='modname']/VALUE/text()='resource'"/>
    <div class="ml-4">
      <xsl:variable name="moduleID" select="KEY[@name='id']/VALUE/text()"/>
      <div class="form-check">

        <input class="form-check-input" type="checkbox" name="module"
               enabled="{$supported}"
               value="{$moduleID}">
          <xsl:if test="$supported">
            <xsl:attribute name="checked">checked</xsl:attribute>
          </xsl:if>
          <xsl:if test="not($supported)">
            <xsl:attribute name="disabled">disabled</xsl:attribute>
          </xsl:if>
        </input>

        <label class="form-check-label" for="module_{$moduleID}">
          <xsl:value-of select="KEY[@name='name']/VALUE/text()"/>
        </label>
      </div>
      <!-- File-Content -->
      <xsl:for-each select="KEY[@name='contents']/MULTIPLE/SINGLE">
        <xsl:call-template name="printFile"/>
      </xsl:for-each>
      <!-- Child-Modules  -->
      <xsl:for-each select="KEY[@name='modules']/MULTIPLE/SINGLE">
        <xsl:call-template name="printModule"/>
      </xsl:for-each>

    </div>
  </xsl:template>

  <xsl:template name="printFile">
    <div class="ml-4">
      <div class="form-check">
        <xsl:variable name="fileID" select="KEY[@name='fileurl']/VALUE/text()"/>
        <input class="form-check-input" type="checkbox" name="file" value="{$fileID}" checked="true"/>
        <label class="form-check-label" for="module_">
          <xsl:value-of select="KEY[@name='filename']/VALUE/text()"/>
        </label>
      </div>
    </div>
  </xsl:template>

</xsl:stylesheet>
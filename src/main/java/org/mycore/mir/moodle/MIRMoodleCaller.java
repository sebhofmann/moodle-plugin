package org.mycore.mir.moodle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

public class MIRMoodleCaller {

    private static final Logger LOGGER = LogManager.getLogger();

    private URL moodleURL;

    private String token;

    public MIRMoodleCaller(URL moodleURL, String token) {
        this.moodleURL = moodleURL;
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public URL getMoodleURL() {
        return moodleURL;
    }

    private Document callMethod(String wsfunction, String... keyValues) throws IOException, JDOMException {
        HashMap<String, String> parameters = new HashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            parameters.put(keyValues[i], keyValues[i + 1]);
        }
        return callMethod(wsfunction, parameters);
    }

    private Document callMethod(String wsfunction, Map<String, String> params) throws IOException, JDOMException {
        final StringBuilder pathBuilder = new StringBuilder(
            getMoodleURL().getPath() + "webservice/rest/server.php?wstoken=$TOKEN$&wsfunction=$FUNCTION$"
                .replace("$TOKEN$", getToken())
                .replace("$FUNCTION$", wsfunction));

        params.forEach((key, val) -> pathBuilder.append("&").append(key).append("=").append(val));

        final URL url = getMoodleURL();
        final URL requestURL = new URL(url.getProtocol(), url.getHost(), url.getPort(), pathBuilder.toString());

        System.out.println(requestURL.toString());

        try (InputStream is = requestURL.openStream()) {
            return new SAXBuilder().build(is);
        }
    }

    public Document resolveCourse(long id) throws IOException, JDOMException {
        return callMethod("core_course_get_courses", "options[ids][0]", String.valueOf(id));
    }

    public Document resolveCourseContent(long id) throws IOException, JDOMException {
        return callMethod("core_course_get_contents", "courseid", String.valueOf(id));
    }

    public Document resolveEnroledUsers(long courseId) throws IOException, JDOMException {
        return callMethod("core_enrol_get_enrolled_users", "courseid", String.valueOf(courseId));
    }

    public Document resolveUsersCourses(long userId) throws IOException, JDOMException {
        return callMethod("core_enrol_get_users_courses", "userid", String.valueOf(userId));
    }

    public Document resolveCourseCategories() throws IOException, JDOMException {
        return callMethod("core_course_get_categories");
    }
}

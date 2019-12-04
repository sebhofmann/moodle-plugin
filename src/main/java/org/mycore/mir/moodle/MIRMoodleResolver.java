package org.mycore.mir.moodle;

import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;

import org.jdom2.JDOMException;
import org.jdom2.transform.JDOMSource;
import org.mycore.common.MCRException;

public class MIRMoodleResolver implements URIResolver {

    public static final MIRMoodleCaller MOODLE_CALLER = new MIRMoodleCaller(MIRMoodleUtils.getMoodleURL(),
        MIRMoodleUtils.getMoodleToken());

    public MIRMoodleResolver() {
    }

    @Override
    public Source resolve(String href, String base) {
        String target = href.substring(href.indexOf(":") + 1);

        final String[] call = target.split(":");
        switch (call[0]) {
            case "resolveCourseContent":
                if (call.length == 2) {
                    try {
                        return new JDOMSource(MOODLE_CALLER.resolveCourseContent(Long.parseLong(call[1])));
                    } catch (IOException | JDOMException e) {
                        throw new MCRException("Error while calling moodle", e);
                    }
                }
                break;
            case "resolveEnroledUsers":
                if (call.length == 2) {
                    try {
                        return new JDOMSource(MOODLE_CALLER.resolveEnroledUsers(Long.parseLong(call[1])));
                    } catch (IOException | JDOMException e) {
                        throw new MCRException("Error while calling moodle", e);
                    }
                }
                break;
            case "resolveCourseCategories":
                try {
                    return new JDOMSource(MOODLE_CALLER.resolveCourseCategories());
                } catch (IOException | JDOMException e) {
                    throw new MCRException("Error while calling moodle", e);
                }
        }
        throw new MCRException("Invalid Moodle Function call " + target + "!");

    }
}

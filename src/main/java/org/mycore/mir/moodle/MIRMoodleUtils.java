package org.mycore.mir.moodle;

import java.net.MalformedURLException;
import java.net.URL;

import org.mycore.common.MCRSessionMgr;
import org.mycore.common.MCRUserInformation;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.common.config.MCRConfigurationException;

public class MIRMoodleUtils {

    public static final String MOOODLE_USER_ID_ATTRIBUTE = "mooodleUserID";

    public static final MCRConfiguration CONFIG = MCRConfiguration.instance();

    public static final String MIR_MOODLE_URL_PROPERTY = "MIR.Moodle.URL";

    public static final String MIR_MOODLE_TOKEN_PROPERTY = "MIR.Moodle.Token";

    public static URL getMoodleURL() {
        final String urlProperty = CONFIG.getString(MIR_MOODLE_URL_PROPERTY);
        try {
            return new URL(urlProperty);
        } catch (MalformedURLException e) {
            throw new MCRConfigurationException(
                "The " + MIR_MOODLE_URL_PROPERTY + ":" + urlProperty + " is not a valid url!", e);
        }
    }

    public static String getMoodleToken() {
        return CONFIG.getString(MIR_MOODLE_TOKEN_PROPERTY);
    }

    public static String getUserId() {
        final MCRUserInformation userInformation = MCRSessionMgr.getCurrentSession().getUserInformation();
        return userInformation.getUserAttribute(MOOODLE_USER_ID_ATTRIBUTE);
    }

}

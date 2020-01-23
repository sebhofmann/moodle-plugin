package org.mycore.mir.moodle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.mycore.access.MCRAccessException;
import org.mycore.access.MCRAccessInterface;
import org.mycore.access.MCRAccessManager;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRException;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRJDOMContent;
import org.mycore.common.content.transformer.MCRContentTransformer;
import org.mycore.common.content.transformer.MCRContentTransformerFactory;
import org.mycore.common.content.transformer.MCRParameterizedTransformer;
import org.mycore.common.xml.MCRLayoutService;
import org.mycore.common.xsl.MCRParameterCollector;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaIFS;
import org.mycore.datamodel.metadata.MCRMetaLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.frontend.servlets.MCRContentServlet;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.tools.MCRTopologicalSort;
import org.mycore.user2.MCRUser;
import org.mycore.user2.MCRUserManager;
import org.xml.sax.SAXException;

import static org.mycore.mir.moodle.MIRMoodleUtils.MOOODLE_USER_ID_ATTRIBUTE;

public class MIRMoodleContentServlet extends MCRContentServlet {

    public static final String MOODLE_CALL_ELEMENT_NAME = "MoodleCall";

    public static final String MOODLE_CALL_METHOD_ATTRIBUTE = "method";

    public static final MCRContentTransformer COURSE2MODS_TRANSFORMER = MCRContentTransformerFactory
        .getTransformer("Course2Mods");

    public static final String MIR_PROJECTID_DEFAULT = "MIR.projectid.default";

    private static final String COURSE_PARAMETER = "course";

    private static final String MODULE_PARAMETER = "module";

    private static final String FILE_PARAMETER = "file";

    private static final Logger LOGGER = LogManager.getLogger();

    public static final String IMPORTED_MOODLE_COURSE_FLAG = "IMPORTED_MOODLE_COURSE";

    public static MCRDerivate createDerivate(String documentID)
        throws MCRPersistenceException, IOException, MCRAccessException {
        final String projectId = MCRObjectID.getInstance(documentID).getProjectId();
        MCRObjectID oid = MCRObjectID.getNextFreeId(projectId, "derivate");
        final String derivateID = oid.toString();

        MCRDerivate derivate = new MCRDerivate();
        derivate.setId(oid);
        derivate.setLabel("data object from " + documentID);

        String schema = MCRConfiguration.instance().getString("MCR.Metadata.Config.derivate", "datamodel-derivate.xml")
            .replaceAll(".xml",
                ".xsd");
        derivate.setSchema(schema);

        MCRMetaLinkID linkId = new MCRMetaLinkID();
        linkId.setSubTag("linkmeta");
        linkId.setReference(documentID, null, null);
        derivate.getDerivate().setLinkMeta(linkId);

        MCRMetaIFS ifs = new MCRMetaIFS();
        ifs.setSubTag("internal");
        ifs.setSourcePath(null);

        derivate.getDerivate().setInternals(ifs);

        LOGGER.debug("Creating new derivate with ID {}", derivateID);
        MCRMetadataManager.create(derivate);

        if (MCRConfiguration.instance().getBoolean("MCR.Access.AddDerivateDefaultRule", true)) {
            MCRAccessInterface aclImpl = MCRAccessManager.getAccessImpl();
            Collection<String> configuredPermissions = aclImpl.getAccessPermissionsFromConfiguration();
            for (String permission : configuredPermissions) {
                MCRAccessManager.addRule(derivateID, permission, MCRAccessManager.getTrueRule(),
                    "default derivate rule");
            }
        }

        final MCRPath rootDir = MCRPath.getPath(derivateID, "/");
        if (Files.notExists(rootDir)) {
            rootDir.getFileSystem().createRoot(derivateID);
        }

        return derivate;
    }

    @Override
    public MCRContent getContent(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        final String userId = MIRMoodleUtils.getUserId();
        final Element content;
        if (userId != null) {
            final String importID = httpServletRequest.getParameter("importID");

            if (importID != null) {
                if (httpServletRequest.getMethod().equals("GET")) {
                    content = listImportableCourseContents(importID);
                } else if (httpServletRequest.getMethod().equals("POST")) {
                    try {
                        content = importSelectedContent(httpServletRequest, importID);
                    } catch (IOException | JDOMException | SAXException e) {
                        throw new MCRException(e);
                    }
                } else {
                    throw new MCRException("The Method " + httpServletRequest.getMethod() + " is not implemented!");
                }
            } else {
                content = listImportableCourses(userId);
            }
        } else if ("GET".equals(httpServletRequest.getMethod()) && "userEdit"
            .equals(httpServletRequest.getParameter("action"))) {
            final String userID = httpServletRequest.getParameter("userID");
            final MCRUser user = MCRUserManager.getCurrentUser();
            final Map<String, String> attributes = user.getAttributes();
            attributes.put(MOOODLE_USER_ID_ATTRIBUTE, userID);
            user.setAttributes(attributes);
            MCRUserManager.updateUser(user);
            content = listImportableCourses(userID);
        } else {
            content = new Element(MOODLE_CALL_ELEMENT_NAME);
            content.setAttribute(MOODLE_CALL_METHOD_ATTRIBUTE, "no_user");
        }

        try {
            return MCRLayoutService.instance().getTransformedContent(httpServletRequest, httpServletResponse,
                new MCRJDOMContent(new Document(content)));
        } catch (IOException | TransformerException | SAXException e) {
            throw new MCRException(e);
        }
    }

    private Element listImportableCourses(String userId) {
        Element content;
        final MIRMoodleCaller moodleCaller = new MIRMoodleCaller(MIRMoodleUtils.getMoodleURL(),
            MIRMoodleUtils.getMoodleToken());
        try {
            Document moodleCall = moodleCaller.resolveUsersCourses(Long.parseLong(userId));
            final Element responseElement = moodleCall.getRootElement().detach();
            content = new Element(MOODLE_CALL_ELEMENT_NAME);
            content.setAttribute(MOODLE_CALL_METHOD_ATTRIBUTE, "core_enrol_get_users_courses");
            content.addContent(responseElement);
        } catch (IOException | JDOMException e) {
            throw new MCRException("Error while receiving courses of user " + userId, e);
        }
        return content;
    }

    private Element importSelectedContent(HttpServletRequest httpServletRequest, String importID)
        throws IOException, JDOMException, SAXException {
        final Map<String, String[]> parameterMap = httpServletRequest.getParameterMap();
        final MIRMoodleCaller moodleCaller = new MIRMoodleCaller(MIRMoodleUtils.getMoodleURL(),
            MIRMoodleUtils.getMoodleToken());

        HashMap<String, Element> idModsElementMap = new HashMap<>();

        final Element createdObjects = new Element("MoodleCall");
        createdObjects.setAttribute("method","importResult");

        final Optional<String> courseOptional = parameterMap
            .entrySet()
            .stream()
            .filter(e -> e.getKey().equals(COURSE_PARAMETER))
            .map(Map.Entry::getValue)
            .map(v -> Stream.of(v).findFirst().orElse(null))
            .findFirst();

        final Document resolvedCourseContent = moodleCaller.resolveCourseContent(Long.parseLong(importID));
        if (courseOptional.isPresent()) {
            final String courseID = courseOptional.get();
            final Document resolvedCourse = moodleCaller.resolveCourse(Long.parseLong(courseID));

            final MCRContent result = COURSE2MODS_TRANSFORMER
                .transform(new MCRJDOMContent(resolvedCourse));

            final Element modsElement = result.asXML().getRootElement().detach();
            idModsElementMap.put(courseID, modsElement);
            // import course here
        }

        final List<String> moduleIdsToImport = parameterMap
            .entrySet()
            .stream()
            .filter(e -> e.getKey().equals(MODULE_PARAMETER))
            .flatMap(e -> Stream.of(e.getValue()))
            .collect(Collectors.toList());
        moduleIdsToImport.forEach(moduleID -> {
            final MCRParameterizedTransformer transformer = (MCRParameterizedTransformer) MCRContentTransformerFactory
                .getTransformer("Module2Mods");
            final MCRParameterCollector parameter = new MCRParameterCollector();
            parameter.setParameter("moduleID", moduleID);
            try {
                final MCRContent result = transformer
                    .transform(new MCRJDOMContent(resolvedCourseContent), parameter);
                final Element modsElement = result.asXML().getRootElement().detach();
                idModsElementMap.put(moduleID, modsElement);
            } catch (IOException | JDOMException | SAXException e) {
                throw new MCRException("Error while Transforming Module", e);
            }
        });

        final List<String> fileUrlsToImport = parameterMap
            .entrySet()
            .stream()
            .filter(e -> e.getKey().equals(FILE_PARAMETER))
            .flatMap(e -> Stream.of(e.getValue()))
            .collect(Collectors.toList());

        final Map<String, String> hierarchyMap = buildHierarchyMap(resolvedCourseContent, courseOptional.orElse(null),
            moduleIdsToImport, fileUrlsToImport);

        // find the right order for the modules to import
        MCRTopologicalSort ts = new MCRTopologicalSort();
        moduleIdsToImport.forEach(ts::addNode);
        courseOptional.ifPresent(ts::addNode);
        HashSet<String> modulesPresentSet = new HashSet<>(moduleIdsToImport);

        hierarchyMap.forEach((child, parent) -> {
            if (modulesPresentSet.contains(child)) {
                // to sort
                final Integer childID = ts.getNodeID(child);
                final Integer parentID = ts.getNodeID(parent);
                ts.addEdge(childID, parentID);
            }
        });

        final int[] order = ts.doTopoSort();
        final List<String> newOrder = Arrays.stream(order)
            .mapToObj(ts::getNodeName)
            .collect(Collectors.toList());

        HashMap<String, String> idMCRIDMap = new HashMap<>();

        newOrder.forEach(id -> {
            final Element mods = idModsElementMap.get(id);
            final MCRObjectID newObjectId = MCRObjectID
                .getNextFreeId(MCRConfiguration.instance().getString(MIR_PROJECTID_DEFAULT) + "_mods");

            idMCRIDMap.put(id, newObjectId.toString());
            final MCRObject mcrObject = MCRMODSWrapper.wrapMODSDocument(mods, newObjectId.getProjectId());
            mcrObject.setId(newObjectId);
            mcrObject.getService().addFlag(IMPORTED_MOODLE_COURSE_FLAG, importID);
            if (hierarchyMap.containsKey(id)) {
                final String parent = hierarchyMap.get(id);
                final String objectID = idMCRIDMap.get(parent);

                final Element relatedItem = new Element("relatedItem", MCRConstants.MODS_NAMESPACE);
                relatedItem.setAttribute("type", "host");
                relatedItem.setAttribute("href", objectID, MCRConstants.XLINK_NAMESPACE);
                relatedItem.setAttribute("type", "simple", MCRConstants.XLINK_NAMESPACE);
                mods.addContent(relatedItem);
            }

            try {
                MCRMetadataManager.create(mcrObject);
            } catch (MCRAccessException e) {
                throw new MCRException("Error while creating MCRObject!", e);
            }

            final Element object = new Element("object");
            object.setAttribute("id", newObjectId.toString());
            createdObjects.addContent(object);
        });

        fileUrlsToImport.forEach(url -> {
            final String moodleParent = hierarchyMap.get(url);
            final String mcrID = idMCRIDMap.get(moodleParent);
            final MCRDerivate derivate;

            try {
                derivate = createDerivate(mcrID);
            } catch (IOException | MCRAccessException e) {
                throw new MCRException("Error while creating Derivate for Object " + mcrID + " for file " + url, e);
            }
            final int pos1 = url.lastIndexOf("/");
            final String fileName;
            try {
                fileName = URLDecoder.decode(url.substring(pos1, url.lastIndexOf("?") > pos1? url.lastIndexOf("?"): url.length()),
                    StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new MCRException("UTF-8 Encoding not supported!");
            }

            try (InputStream is = new URL(url + "&token=" + MIRMoodleUtils.getMoodleToken()).openStream()) {
                Files.copy(is, MCRPath.getPath(derivate.toString(), fileName));
            } catch (IOException e) {
                throw new MCRException("Error while Requesting file!", e);
            }

            derivate.getDerivate().getInternals().setMainDoc(fileName);
            try {
                MCRMetadataManager.update(derivate);
            } catch (MCRAccessException e) {
                throw new MCRException("Error while setting main file!", e);
            }
        });

        return createdObjects;
    }

    private Map<String, String> buildHierarchyMap(Document courseContent, String courseID,
        List<String> moduleIdsToImport, List<String> fileUrlsToImport) {
        HashSet<String> modulesPresentSet = new HashSet<>(moduleIdsToImport);
        final HashMap<String, String> childParentMap = new HashMap<>();

        moduleIdsToImport.forEach(id -> {
            final XPathExpression<Element> moduleXpath = XPathFactory.instance()
                .compile("//SINGLE[KEY[@name='id' and VALUE/text()='" + id + "']]", Filters.element());
            final Element module = moduleXpath.evaluateFirst(courseContent);

            final String parent = findParent(module, courseID, modulesPresentSet);
            if (parent != null) {
                childParentMap.put(id, parent);
            } else if (courseID != null) {
                childParentMap.put(id, courseID);
            }
        });

        fileUrlsToImport.forEach(url -> {
            final XPathExpression<Element> moduleXpath = XPathFactory.instance()
                .compile("//SINGLE[KEY[@name='fileurl' and VALUE/text()='" + url + "']]", Filters.element());
            final Element file = moduleXpath.evaluateFirst(courseContent);

            final String parent = findParent(file, courseID, modulesPresentSet);
            if (parent != null) {
                childParentMap.put(url, parent);
            } else if (courseID != null) {
                childParentMap.put(url, courseID);
            }
        });

        return childParentMap;
    }

    private String findParent(Element current, String courseID, Set<String> moduleIdsToImport) {
        final Element parentElement = current.getParentElement();
        if (parentElement == null) {
            return null;
        }
        if ("KEY".equals(parentElement.getName()) && ("modules".equals(parentElement.getAttributeValue("name"))
            || "contents".equals(parentElement.getAttributeValue("name")))) {
            final Optional<String> id = parentElement.getParentElement()
                .getChildren("KEY")
                .stream()
                .filter(el -> "id".equals(el.getAttributeValue("name")))
                .map(el -> el.getChild("VALUE"))
                .map(Element::getTextNormalize)
                .findFirst();

            if (!id.isPresent()) {
                // this should not happen
                throw new MCRException("Module without id found!");
            }

            final String idOfParent = id.get();
            if (moduleIdsToImport.contains(idOfParent)) {
                return idOfParent;
            }
        }
        return findParent(parentElement, courseID, moduleIdsToImport);
    }

    private Element listImportableCourseContents(String importID) {
        Element content;
        final MIRMoodleCaller moodleCaller = new MIRMoodleCaller(MIRMoodleUtils.getMoodleURL(),
            MIRMoodleUtils.getMoodleToken());
        try {
            final Document courseContent = moodleCaller.resolveCourse(Long.parseLong(importID));
            final Element responseElement = courseContent.getRootElement().detach();
            content = new Element(MOODLE_CALL_ELEMENT_NAME);
            content.setAttribute(MOODLE_CALL_METHOD_ATTRIBUTE, "core_course_get_courses");
            content.addContent(responseElement);
        } catch (IOException | JDOMException e) {
            throw new MCRException("Error while receiving course with id" + importID);
        }
        return content;
    }

}

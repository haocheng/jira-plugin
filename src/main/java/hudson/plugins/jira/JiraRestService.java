package hudson.plugins.jira;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JiraRestService {

    public static final int TIMEOUT_IN_10_SECONDS = 10000;
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    private final String url;

    private final JiraRestClient jiraRestClient;

    private final ObjectMapper objectMapper;

    private final String authHeader;

    public JiraRestService(String url, String username, String password) {
        this.url = url;
        this.objectMapper = new ObjectMapper();
        final String login = username + ":" + password;
        try {
            byte[] encodeBase64 = Base64.encodeBase64(login.getBytes("UTF-8"));
            this.authHeader = "Basic " + new String(encodeBase64, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("failed to encode username:password using Base64");
        }

        jiraRestClient = new AsynchronousJiraRestClientFactory()
                .createWithBasicHttpAuthentication(URI.create(url), username, password);
    }

    public void addComment(String issueId, String commentBody,
                                         String groupVisibility, String roleVisibility)
            throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
        final URIBuilder builder = new URIBuilder(url)
                .setPath(String.format("/rest/api/2/issue/%s/comment", issueId));

        final Comment comment;
        if (StringUtils.isNotBlank(groupVisibility)) {
            comment = Comment.createWithGroupLevel(commentBody, groupVisibility);
        } else if (StringUtils.isNotBlank(roleVisibility)) {
            comment = Comment.createWithRoleLevel(commentBody, roleVisibility);
        } else {
            comment = Comment.valueOf(commentBody);
        }

        jiraRestClient.getIssueClient().addComment(builder.build(), comment).get(10, TimeUnit.SECONDS);
    }

    public Issue getJiraIssue(String id)
            throws InterruptedException, ExecutionException, TimeoutException {
        return jiraRestClient.getIssueClient().getIssue(id).get(10, TimeUnit.SECONDS);
    }

    public List<IssueType> getIssueTypes() throws InterruptedException, ExecutionException, TimeoutException {
        return Lists.newArrayList(jiraRestClient.getMetadataClient().getIssueTypes().get(10, TimeUnit.SECONDS));
    }

    public List<String> getProjectsKeys() throws IOException, URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
        final Iterable<BasicProject> projects = jiraRestClient.getProjectClient().getAllProjects().get(10, TimeUnit.SECONDS);
        final List<String> keys = new ArrayList<String>();
        for (BasicProject project : projects) {
            keys.add(project.getKey());
        }
        return keys;
    }

    public List<Issue> getIssuesFromJqlSearch(String jqlSearch) throws InterruptedException, ExecutionException, TimeoutException {
        final SearchResult searchResult = jiraRestClient.getSearchClient().searchJql(jqlSearch, 50, 0, null).get(10, TimeUnit.SECONDS);

        return Lists.newArrayList(searchResult.getIssues());
    }

    public List<Version> getVersions(String projectKey) throws URISyntaxException, IOException {
        final URIBuilder builder = new URIBuilder(url)
                .setPath(String.format("/rest/api/2/project/%s/versions", projectKey));

        final Content content = buildGetRequest(builder.build())
                .execute()
                .returnContent();

        final List<Map<String, Object>> decoded = objectMapper.readValue(content.asString(),
                new TypeReference<List<Map<String, Object>>>() {
                });

        final List<Version> versions = new ArrayList<Version>();
        for (Map<String, Object> decodedVersion : decoded) {
            final DateTime releaseDate = decodedVersion.containsKey("releaseDate") ? DATE_TIME_FORMATTER.parseDateTime((String) decodedVersion.get("releaseDate")) : null;
            final Version version = new Version(URI.create((String) decodedVersion.get("self")), Long.parseLong((String) decodedVersion.get("id")),
                    (String) decodedVersion.get("name"), (String) decodedVersion.get("description"), (Boolean) decodedVersion.get("archived"),
                    (Boolean) decodedVersion.get("released"), releaseDate);
            versions.add(version);
        }
        return versions;
    }

    private Request buildGetRequest(URI uri) {
        return Request.Get(uri)
                .connectTimeout(TIMEOUT_IN_10_SECONDS)
                .socketTimeout(TIMEOUT_IN_10_SECONDS)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json");
    }

//    private Request buildPostRequest(URI uri) {
//        return Request.Post(uri)
//                .connectTimeout(10000)
//                .socketTimeout(10000)
//                .addHeader("Authorization", authHeader)
//                .addHeader("Content-Type", "application/json");
//    }

}
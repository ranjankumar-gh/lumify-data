package com.altamiracorp.reddawn.crawler;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class WebCrawl {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebCrawl.class);

    private CommandLineParser parser;
    private CommandLine cl;
    private ArrayList<SearchEngine> engines;
    private ArrayList<Query> queries, rssLinks, redditQueries;
    private Crawler crawler;
    private int results;
    public static final int DEFAULT_RESULT_COUNT = 20;

    public WebCrawl() {
        parser = new PosixParser();
        engines = new ArrayList<SearchEngine>();
        queries = new ArrayList<Query>();
        rssLinks = new ArrayList<Query>();
        redditQueries = new ArrayList<Query>();
        results = -1;
    }

    protected void setCommandLine(CommandLine cl) {
        this.cl = cl;
    }

    protected void setCrawler(Crawler crawler) {
        this.crawler = crawler;
    }

    public void prepare(String[] args) {
        loadCommandLine(args);

        verifyParameters();

        if (getQueryParam() != null) {
            for (String s : getQueryParam().split(",")) {
                Query search = addSearchQuery(s);
                addRedditQueries(search);
            }
        } else {
            addRedditQueries(new Query());
        }

        addRSSLinks();
        setResultCount();
        addEngines();
    }

    private void verifyParameters() {
        if (cl.getOptionValue("provider").equalsIgnoreCase("rss")) {
            if (cl.getOptionValue("rss") == null) {
                LOGGER.error("URL to a valid RSS feed must be specified for search provider \'rss.\'");
                printHelpAndExit();
            }
        } else if (cl.getOptionValue("provider").equalsIgnoreCase("reddit")) {
            if (cl.getOptionValue("count") == null) {
                LOGGER.error("Result count must be specified for search provider \'reddit.\'");
                printHelpAndExit();
            }
        } else if (cl.getOptionValue("query") == null || cl.getOptionValue("count") == null) {
            LOGGER.error("Query and result count must be specified");
            printHelpAndExit();
        }
    }

    protected void loadCommandLine(String[] args) {
        if (args == null || args.length == 0) {
            printHelpAndExit();
        }

        try {
            cl = parser.parse(createOptions(), args);
        } catch (ParseException e) {
            LOGGER.error("The options could not be parsed, please try again or use --help for more information");
            System.exit(1);
        }
        String directory = cl.getOptionValue("directory");
        File file = new File(directory);
        if (!file.exists()) {
            file.mkdir();
        }

        crawler = new Crawler(directory);
    }

    private void printHelpAndExit() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("WebCrawl.sh", createOptions());
        System.exit(0);
    }

    protected Query addSearchQuery(String queryString) {
        Map<String, ArrayList<String>> queryTerms = parseQuery(queryString.trim());
        Query q = new Query();

        for (String type : queryTerms.keySet()) {
            for (String term : queryTerms.get(type)) {
                if (type.equals("optional")) q.addOptionalTerm(term);
                else if (type.equals("excluded")) q.addExcludedTerm(term);
                else if (type.equals("required")) q.addRequiredTerm(term);
            }
        }
        if (!q.getQueryString().equals("")) queries.add(q);

        return q;
    }

    protected void addRedditQueries(Query q) {
        String subreddits = cl.getOptionValue("subreddit");

        if (subreddits != null && subreddits.trim().length() > 0) {
            for (String subreddit : subreddits.split(",")) {
                if (subreddit.trim().length() == 0) continue;
                Query redditQ = q.clone();
                if (subreddit.trim().equals("all")) redditQ.clearSubreddit();
                else redditQ.setSubreddit(subreddit);
                redditQueries.add(redditQ);
            }
        } else {
            redditQueries.add(q);
        }
    }

    protected void addRSSLinks() {
        if (cl.getOptionValue("rss") != null) {
            for (String feed : cl.getOptionValue("rss").split(",")) {
                if (feed.trim().length() == 0) continue;
                Query rssFeed = new Query();
                rssFeed.setRSSFeed(feed);
                rssLinks.add(rssFeed);
            }
        }
    }

    protected void addEngines() {
        ArrayList<String> enginesAdded = new ArrayList<String>();
        for (String s : cl.getOptionValue("provider").split(",")) {
            String trimmed = s.trim();
            if (!enginesAdded.contains(trimmed.toLowerCase())) {
                SearchEngine engine = new GoogleNewsSearchEngine(crawler);
                ArrayList<Query> queryList = queries;

                if (trimmed.equalsIgnoreCase("google")) {
                    engine = new GoogleSearchEngine(crawler);
                    queryList = queries;
                } else if (trimmed.equalsIgnoreCase("news")) {
                    engine = new GoogleNewsSearchEngine(crawler);
                    queryList = queries;
                } else if (trimmed.equalsIgnoreCase("reddit")) {
                    engine = new RedditSearchEngine(crawler);
                    queryList = redditQueries;
                } else if (trimmed.equalsIgnoreCase("rss")) {
                    engine = new RSSEngine(crawler);
                    queryList = rssLinks;
                } else if (trimmed.equalsIgnoreCase("flickr")) {
                    engine = new FlickrSearchEngine(crawler);
                    queryList = queries;
                }

                for (Query q : queryList) engine.addQueryToQueue(q, results);
                engines.add(engine);

                enginesAdded.add(trimmed.toLowerCase());
            }
        }
    }

    protected void setResultCount() {
        try {
            String countParam = cl.getOptionValue("count");
            if (countParam != null) {
                results = Integer.parseInt(countParam);
                if (results < 1) results = DEFAULT_RESULT_COUNT;
            }
        } catch (NumberFormatException e) {
            results = DEFAULT_RESULT_COUNT;
        }
    }

    public void run() {
        for (SearchEngine engine : engines) engine.runQueue();
    }

    public String getQueryParam() {
        return cl.getOptionValue("query");
    }

    public static Options createOptions() {
        Options options = new Options();
        options.addOption("d", "directory", true, "The absolute path of the directory to where the files will be written - required");
        options.addOption("p", "provider", true, "The search provider(s) to use for this query, separated by commas for multiple (options: google, news, reddit, rss, flickr) - required");
        options.addOption("q", "query", true, "The query/queries you want to perform (separate multiple with commas) - required for google, news and flickr providers, optional for reddit");
        options.addOption("c", "count", true, "The number of results to return from each query performed - required for google, news, reddit and flickr providers, optional for rss provider");
        options.addOption("r", "rss", true, "The RSS feed URL(s) to fetch for this query (separate multiple with commas) - required for rss provider");
        options.addOption("s", "subreddit", true, "The subreddit(s) to fetch (optionally filtered by query)");
        return options;
    }

    public static Map<String, ArrayList<String>> parseQuery(String query) {
        Map<String, ArrayList<String>> params = new TreeMap<String, ArrayList<String>>();
        params.put("optional", new ArrayList<String>());
        params.put("required", new ArrayList<String>());
        params.put("excluded", new ArrayList<String>());

        // Gets and returns each term in the query (assumes space-separated terms)
        for (String term : query.split(" ")) {
            if (term.trim().length() == 0) continue;
            if (term.charAt(0) == '+') params.get("required").add(term.substring(1));
            else if (term.charAt(0) == '-') params.get("excluded").add(term.substring(1));
            else params.get("optional").add(term);
        }

        return params;
    }

    public ArrayList<SearchEngine> getEngines() {
        return engines;
    }

    public ArrayList<Query> getQueries() {
        return queries;
    }

    public ArrayList<Query> getRssLinks() {
        return rssLinks;
    }

    public ArrayList<Query> getRedditQueries() {
        return redditQueries;
    }

    public int getResults() {
        return results;
    }

    public static void main(String[] args) {
        WebCrawl driver = new WebCrawl();
        driver.prepare(args);
        driver.run();
    }
}


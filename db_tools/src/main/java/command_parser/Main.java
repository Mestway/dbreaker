package command_parser;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;

import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.commons.dbcp.BasicDataSource;

import org.json.*;

public class Main {

    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";

    public static List<String> simpleScriptParser(String content) {
        List<String> result = new ArrayList<String>();
        String[] statements = content.split(";");
        for (String s : statements) {
            String stripped = s.trim();
            if (stripped != "")
                result.add(stripped);
        }
        return result;
    }

    public static List<String> readFileContent(String fileName) throws FileNotFoundException {
        // read queries from the file
        List<String> result = new ArrayList<String>();
        File file = new File(fileName);
        Scanner sc = new Scanner(file);
        while (sc.hasNextLine())
            result.add(sc.nextLine());
        return result;
    }

    public static void main(String[] args)
            throws ClassNotFoundException, SQLException, ArgumentParserException, IOException {

        ArgumentParser parser = ArgumentParsers.newFor("db_tools")
                .build().description("The tool to connect Calcite to MySQL.");

        parser.addArgument("--database").metavar("DATABASE").setDefault("test_db");
        parser.addArgument("--user").metavar("USERNAME").setDefault("dbtest");
        parser.addArgument("--password").metavar("PASSWORD").setDefault("dbtest");
        parser.addArgument("--ddl").metavar("DDL_FILE");
        parser.addArgument("--query").metavar("QUERY_FILE");
        parser.addArgument("--output-file").metavar("OUTPUT_FILE");

        Namespace arguments = parser.parseArgs(args);

        //  Database information and credentials
        final String DB_URL = "jdbc:mysql://localhost/" + arguments.get("database");
        final String USER = arguments.get("user");
        final String PASS = arguments.get("password");
        final String ddlFile = arguments.get("ddl");
        final String queryFile = arguments.get("query");
        final String outputFile = arguments.get("output_file");

        System.out.println(DB_URL + " " + USER + " " + PASS + " " + ddlFile + " " + queryFile);

        List<String> ddlCommands = simpleScriptParser(String.join(" ", readFileContent(ddlFile)));

        Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
        Statement ddlStatement = conn.createStatement();
        // execute ddl
        for (String q : ddlCommands)
            ddlStatement.addBatch(q);
        ddlStatement.executeBatch();
        conn.close();

        List<String> queries = simpleScriptParser(String.join(" ", readFileContent(queryFile)));

        Class.forName("org.apache.calcite.jdbc.Driver");

        Properties info = new Properties();
        info.setProperty("lex", "JAVA");
        Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
        CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
        SchemaPlus rootSchema = calciteConnection.getRootSchema();

        // create schema
        Class.forName(JDBC_DRIVER);
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(DB_URL);
        dataSource.setUsername(USER);
        dataSource.setPassword(PASS);
        Schema schema = JdbcSchema.create(rootSchema, "test_db",
                dataSource, null, "test_db");

        rootSchema.add("test_db", schema);
        Statement statement = calciteConnection.createStatement();

        // execute query
        for (String q : queries) {
            ResultSet rs = statement.executeQuery(q.replace(";", ""));
            String resultJson = resultToJsonStr(rs);
            if (outputFile != null) {
                FileWriter fw = new FileWriter(new File(outputFile));
                fw.write(resultJson);
            } else {
                System.out.println(resultJson);
            }
            rs.close();
        }

        statement.close();
        connection.close();
    }

    private static String resultToJsonStr(ResultSet resultSet) throws SQLException {

        JSONObject output = new JSONObject();
        JSONArray content = new JSONArray();

        final ResultSetMetaData metaData = resultSet.getMetaData();
        final int columnCount = metaData.getColumnCount();

        List<String> columnTypes = new ArrayList<String>();
        List<String> columnNames = new ArrayList<String>();
        for (int i = 1; i <= columnCount; i ++) {
            columnNames.add(metaData.getColumnLabel(i));
            columnTypes.add(metaData.getColumnTypeName(i));
        }
        output.put("header", columnNames);
        output.put("type", columnTypes);

        while (resultSet.next()) {
            JSONArray row = new JSONArray();
            for (int i = 1; i <= columnCount; i++) {
                row.put(resultSet.getObject(i));
            }
            content.put(row);
        }
        output.put("content", content);
        return output.toString();
    }
}
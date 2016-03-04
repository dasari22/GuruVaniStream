/**
 Copyright:
 2015/2016 Sunny Dasari
 developer.google.com

 This file is part of AustrianPublicStream.

 GuruVani is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 GuruVani is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with GuruVani.  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.guruvani.stream;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * ORF JSON Parser
 *
 * This class is able to parse the JSON input of a full program list.
 * Usually retrieved via: http://oe1.orf.at/programm/konsole/tag/<yyyymmdd>
 *
 * In addition this class provides the offline programs (accessed via an XML file)
 */
public class ORFParser {
    private ArrayList<ORFProgram> programList;
    public final static String ORF_FULL_BASE_URL = "http://oe1.orf.at/programm/konsole/tag/";
    public final static String ORF_LIVE_URL = "http://mp3stream3.apasf.apa.at:8000/;stream.mp3";
    //public final static String ORF_LIVE_URL = "http://listen.radionomy.com:80/SwastikTeluguBhajansDhuns";
    public final static String OFFLINE_XML_NAME = "oe1_offline.xml";

    public final static String XML_PROGRAM = "program";
    public final static String XML_ID = "id";
    public final static String XML_TIME = "time";
    public final static String XML_TITLE = "title";
    public final static String XML_SHORTTITLE = "shorttitle";
    public final static String XML_INFO = "info";
    public final static String XML_URL = "url";
    public final static String XML_FILENAME = "filename";
    public final static String XML_DAYLABEL = "daylabel";
    public static String LOCAL_DATA = "localData/discoursesList.json";

    /*public static String ORF_DURATION_URL1 = "http://oe1.orf.at/programm/";
    public static String ORF_DURATION_URL2 = "/playlist";*/

    private String fetchURL(URL orfURL) throws IOException {
        //flag of being in the program list
        //boolean inList = false;


        StringBuilder result = new StringBuilder();
        URL url = new URL(orfURL.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        return this.parse(result.toString());
    }

    private String parse(String s) {
        //recreate program list each time
        programList = new ArrayList<>();

        try {
            //open JSON object
            JSONObject programOriginal = new JSONObject(s);
            //load only list item (contains all single programs)
            JSONArray programListJSON = programOriginal.getJSONArray("list");

            //iterate
            for(int i = 0; i < programListJSON.length(); i++) {
                //load one program item of the list
                JSONObject programItems = programListJSON.getJSONObject(i);
                //create a temp object
                ORFProgram currentProgram = new ORFProgram();

                //add all parameters
                currentProgram.id = Integer.valueOf((String)programItems.get("id"));
                currentProgram.time = (String)programItems.get("time");
                currentProgram.title = (String)programItems.get("title");
                currentProgram.shortTitle = (String)programItems.get("short_title");
                currentProgram.info = (String)programItems.get("info");
                currentProgram.url = (String)programItems.get("url_stream");
                currentProgram.dayLabel = (String)programItems.get("day_label");


                //add temp program to list
                programList.add(currentProgram);
            }

        } catch (JSONException e) {
            Log.e("ORFParser", "\n" + "List does not fit...");
            return "List is invalid , please contact the Sunny";
        }
        return "";
    }

    public ArrayList<ORFProgram> getProgramsForDay(Date day) {
        Calendar dayCalendar = new GregorianCalendar();
        dayCalendar.setTime(day);

        try {
            String month = String.format("%1$02d", dayCalendar.get(Calendar.MONTH)+1);
            String daynr = String.format("%1$02d", dayCalendar.get(Calendar.DAY_OF_MONTH));

            String fullURLString = ORF_FULL_BASE_URL + dayCalendar.get(Calendar.YEAR) + month + daynr;
            this.fetchURL(new URL(fullURLString));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //return LOCAL_DATA;
        return this.programList;
    }

    public ArrayList<ORFProgram> getProgramsOffline(File cacheDir) {
        ArrayList<ORFProgram> result = new ArrayList<>();
        //ArrayList<ORFProgram> result = new ArrayList<ORFProgram>();

        //open the XML file
        try {
            Document doc;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new FileReader(new File(cacheDir, OFFLINE_XML_NAME)));
            doc = db.parse(is);

            NodeList nl = doc.getElementsByTagName(XML_PROGRAM);

            //iterate all "program" nodes
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                //fetch all XML tags
                String daylabel = ORFParser.getValue(e, XML_DAYLABEL);
                //String fileName = ORFParser.getValue(e, XML_FILENAME);
                String url = ORFParser.getValue(e, XML_URL);
                String info = ORFParser.getValue(e, XML_INFO);
                String title = ORFParser.getValue(e, XML_TITLE);
                String time = ORFParser.getValue(e, XML_TIME);
                String id = ORFParser.getValue(e, XML_ID);
                String shortTitle = ORFParser.getValue(e, XML_SHORTTITLE);

                File file = new File(url);
                if (file.exists()) {
                    ORFProgram temp = new ORFProgram();
                    temp.dayLabel = daylabel;
                    temp.url = url;
                    temp.info = info;
                    temp.title = title;
                    temp.shortTitle = shortTitle;
                    temp.time = time;
                    temp.id = Integer.valueOf(id);
                    result.add(temp);
                }
            }
            return result;
        } catch (Exception e) {
            Log.e("PUBLICSTREAM", e.getMessage());
            return null;
        }
    }

    public void removeProgramOffline(ORFProgram program, File cacheDir) {
        try {
            //open the XML file
            Document doc;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Element root;
            try {
                InputSource is = new InputSource();
                is.setCharacterStream(new FileReader(new File(cacheDir, OFFLINE_XML_NAME)));
                doc = db.parse(is);
                root = doc.getDocumentElement();
            } catch (FileNotFoundException e) {
                return;
            }

            NodeList currentList = root.getElementsByTagName(XML_PROGRAM);
            String fileNameToDelete = "";

            for(int i = 0; i<currentList.getLength(); i++) {
                Node deleteProgram = currentList.item(i);
                NodeList deleteNodeList = deleteProgram.getChildNodes();
                boolean delete = false;

                for(int j = 0; j<deleteNodeList.getLength(); j++) {
                    switch (deleteNodeList.item(j).getNodeName()) {
                        case XML_ID:
                            delete = deleteNodeList.item(j).getFirstChild().getNodeValue().equals(String.valueOf(program.id));
                            break;
                        case XML_DAYLABEL:
                            delete = deleteNodeList.item(j).getFirstChild().getNodeValue().equals(String.valueOf(program.dayLabel));
                            break;

                        case XML_FILENAME:
                            fileNameToDelete = deleteNodeList.item(j).getFirstChild().getNodeValue();
                        //only compare 2 fields (id and day, should be enough)
                        default:
                            break;
                    }
                }

                if(delete)
                {
                    root.removeChild(deleteProgram);
                    if(!fileNameToDelete.equals("")) {
                        File del = new File(fileNameToDelete);
                        //noinspection ResultOfMethodCallIgnored
                        del.delete();
                        Log.d("FILE", "Removed File: " + fileNameToDelete);
                    }
                    break;
                }
            }

            //Rewrite the XML to the external storage (cache)...
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            FileWriter writer = new FileWriter(new File(cacheDir, OFFLINE_XML_NAME));
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
        } catch (Exception e) {
            Log.e("PUBLICSTREAM","XML write failed: " + e.getMessage());
        }

    }

    public void addProgramOffline(ORFProgram program, File cacheDir) {

        try {
            //open the XML file
            Document doc;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Element root;
            try {
                InputSource is = new InputSource();
                is.setCharacterStream(new FileReader(new File(cacheDir, OFFLINE_XML_NAME)));
                doc = db.parse(is);
                root = doc.getDocumentElement();
            } catch (FileNotFoundException e) {
                doc = db.newDocument();
                root = doc.createElement("root");
                doc.appendChild(root);
            }

            //Add new program
            Element newProgram = doc.createElement(XML_PROGRAM);

            //Add all XML tags to the new program
            Element title = doc.createElement(XML_TITLE);
            title.appendChild(doc.createTextNode(program.title));
            newProgram.appendChild(title);

            Element shorttitle = doc.createElement(XML_SHORTTITLE);
            shorttitle.appendChild(doc.createTextNode(program.shortTitle));
            newProgram.appendChild(shorttitle);

            Element id = doc.createElement(XML_ID);
            id.appendChild(doc.createTextNode(String.valueOf(program.id)));
            newProgram.appendChild(id);

            Element time = doc.createElement(XML_TIME);
            time.appendChild(doc.createTextNode(program.time));
            newProgram.appendChild(time);

            Element url = doc.createElement(XML_URL);
            url.appendChild(doc.createTextNode(program.url));
            newProgram.appendChild(url);

            Element info = doc.createElement(XML_INFO);
            info.appendChild(doc.createTextNode(program.info));
            newProgram.appendChild(info);

            Element filename = doc.createElement(XML_FILENAME);
            filename.appendChild(doc.createTextNode(program.url));
            newProgram.appendChild(filename);

            Element daylabel = doc.createElement(XML_DAYLABEL);
            daylabel.appendChild(doc.createTextNode(program.dayLabel));
            newProgram.appendChild(daylabel);

            root.appendChild(newProgram);

            //Rewrite the XML to the external storage (cache)...
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            FileWriter writer = new FileWriter(new File(cacheDir, OFFLINE_XML_NAME));
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
        } catch (Exception e) {
            Log.e("PUBLICSTREAM","XML write failed: " + e.getMessage());
        }
    }

    private static String getValue(Element item, String str) {
        NodeList n = item.getElementsByTagName(str);
        return ORFParser.getElementValue(n.item(0));
    }

    private static String getElementValue( Node elem ) {
        Node child;
        if( elem != null){
            if (elem.hasChildNodes()){
                for( child = elem.getFirstChild(); child != null; child = child.getNextSibling() ){
                    if( child.getNodeType() == Node.TEXT_NODE  ){
                        return child.getNodeValue();
                    }
                }
            }
        }
        return "";
    }

    public static class ORFProgram {
        public int id;
        //public String length;
        public String time;
        public String title;
        public String shortTitle;
        public String info;
        public String url;
        public String dayLabel;
    }
}

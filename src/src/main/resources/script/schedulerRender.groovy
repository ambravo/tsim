  /////////////////////////////////////////////
 // 2019 - Ariel Bravo-Ayala - MIT License  //
/////////////////////////////////////////////

//Internal
import com.sap.gateway.ip.core.customdev.util.Message
import org.osgi.framework.FrameworkUtil
import groovy.json.*
import java.time.ZoneId
import java.time.LocalDate
import java.time.Duration
import java.time.Instant

//External
import org.joda.time.DateTime
import net.redhogs.cronparser.CronExpressionDescriptor
import net.redhogs.cronparser.Options
import org.quartz.CronScheduleBuilder
import org.quartz.CronTrigger
import org.quartz.TriggerBuilder

def Message schedulerRender(Message message) {
    queryValues = queryToMap(message.getHeaders().CamelHttpQuery)
    tenantInfo = getTenantInfo(message.getHeaders().CamelHttpUrl)
    
    def daysAhead = 7
    if (queryValues.daysAhead){
        daysAhead = queryValues.daysAhead.toInteger()
    }
    
    def hourThreshold = 6
    if (queryValues.hourThreshold || queryValues.hourThreshold > 0){
        hourThreshold = queryValues.hourThreshold.toInteger()
    }
    
    def artefacts = []
    def artefactAndCron = [:]
    def bundles = getBundleContext().getBundles().findAll() 
    def now = DateTime.now().toDate()
    def end = DateTime.now().plusDays(daysAhead).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(999).toDate() 

    //Get main data
    bundles.findAll{ b ->
        if (b.getHeaders().get('SAP-NodeType') == "IFLMAP"){
            artefactAndCron = [:]
            artefactAndCron.id = b.getSymbolicName()
            artefactAndCron.name = b.getHeaders().get('Bundle-Name')
            artefactAndCron.version = b.getVersion().toString()
            artefactAndCron.location =b.getLocation()
            artefactAndCron.cronEntries = getCronEntries(b,now,end,hourThreshold)
            if (artefactAndCron.cronEntries){
                artefacts << artefactAndCron                    
            }
        }
    }
    if (queryValues.mode == "artefacts"){
        mapHeaders = ['content-type':'application/json']
        message.setHeaders(mapHeaders)    
        message.setBody(JsonOutput.toJson(artefacts))
        return message        
    }
        
    //Render TimeLine Json
    def tsJson = renderTSJson(artefacts,hourThreshold,tenantInfo)
    if (queryValues.mode == "ts"){
        mapHeaders = ['content-type':'application/json']
        message.setHeaders(mapHeaders)    
        message.setBody(JsonOutput.toJson(tsJson))
        return message        
    }    
    
    //Render WebPage
    def webPage = renderHTML(tsJson,tenantInfo)
    
    mapHeaders = ['content-type':'text/html']
    message.setHeaders(mapHeaders)    
    message.setBody(webPage) 
    return message
}

def renderHTML(tsJson,tenantInfo){
    html = """
<!DOCTYPE html>
<link title="timeline-styles" rel="stylesheet" href="https://cdn.knightlab.com/libs/timeline3/latest/css/timeline.css">
<html lang="en">
<head>
    <title>TSIM: """ + "${tenantInfo.id} - ${tenantInfo.region}"+"""</title>
    <meta charset="utf-8">
    <link rel="shortcut icon"type="image/x-icon" href="data:image/x-icon;,">
    <script>
        var favIcon = 
        "AAABAAEAEBAAAAEAIABoBAAAFgAAACgAAAAQAAAAIAAAAAEAIAAAAAAAAAQAAMMOAADDDgAAAAAAAAAAAABbC0XAXA5HVV0ZUAtcDUYAZmmPAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGNNeABcEEkAXRhPC1wOSFRbCkS9WwhD/1sIQ/JbCkSuXA5ISF0aUAlcDEYAa4+qAAAAAAAAAAAAZ2aLAFwMRgBdG1ELXA9IS1sKRLBbCEPyWwdC/1sIQ/9bB0L/WwdC/1sIQ/NbCkSuXA9IR10bUQlcEEkAWwxGAF0bUQxcDkdOWwpEs1sIQ/RbB0L/WwdC/1sHQv9bCEP/WwdC/1sHQv9bB0L/WwdC/1sIQ/NbCkWsXA9ISFwOSFJbCkS3WwhD9VsHQv9bB0L/WwdC/1sHQv9bB0L/WwhD/1sHQv9bB0L/WwdC/1sHQv9bB0L/WwdC/1sIQ/VbCEP3WwdC/1sHQv9bB0L/WwdC/1sHQv9bB0L/WwdC/1sIQ/9bB0L/WwdC/1sHQv9bB0L/WwdC/1sGQf9bCEP/WwhD/1sGQf9bB0L/WwdC/1sHQv9bB0L/WwdC/1sHQv9bCEP/WwdC/1sHQv9bBkH/WwVA/1sFQf9dFU3/YDNk/2AyY/9dFEz/WwVB/1sFQf9bBUH/WwdC/1sHQv9bB0L/WwhD/1sHQv9bBkH/XhxS/2AwYv9dGVD/YTdn/2NHdP9iQW//YTZm/14fVf9fK17/Xypd/1wOSP9bB0L/WwdC/1sIQ/9bB0L/WwZB/18kWP9jRXH/XiFW/2E8av9jRnP/YkJv/2E6av9fJVn/YTho/2E2Zv9cEUr/WwZC/1sHQv9bCEP/WwdC/1sHQv9bBUD/WwRA/1sEQP9dGE//Yj9t/2I9bP9dGE//WwM//1sEQP9bAz//WwZB/1sHQv9bB0L/WwhD/1sHQv9bBkH/XRtR/10ZUP9eH1X/YDFi/14hVv9gM2T/Xyhc/14jV/9cEUn/XiNX/1wLRf9bB0L/WwdC/1sIQ/9bB0L/WwM//2dojf9mYYf/ZFJ8/3G7zP9qgJ//bqS7/2qAn/9rjqr/ZV2E/2yXsf9eHVP/WwZB/1sHQv9bCEP/WwZB/1wPSP9riqf/aoOh/2l6mv9ywtL/ZVqC/22Xsf9nbJD/bqS7/3PM2v9xwND/Xh5U/1sGQf9bB0L/WwhD/1sFQf9eH1X/aXyc/2l5mv9lWYH/aX2d/2NKdv9pepv/ZmWK/2Zjif9kU3z/Z2qO/10UTP9bBkH/WwdC/1sIQ/9bB0L/WwdC/1sGQf9bBkH/WwVB/1sGQf9bB0L/WwZB/1sGQf9bBkH/WwVA/1sGQf9bB0L/WwdC/1sHQv9bCUT1WwhD9VsIQ/VbCEP1WwhD9VsIQ/VbCEP1WwhD9VsIQ/VbCEP1WwhD9VsIQ/VbCEP1WwhD9VsIQ/VbCEP1H/gAAAfgAAABgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
        var docHead = document.getElementsByTagName('head')[0];       
        var newLink = document.createElement('link');
        newLink.rel = 'shortcut icon';
        newLink.href = 'data:image/png;base64,'+favIcon;
        docHead.appendChild(newLink);
    </script>
    <script src="https://code.jquery.com/jquery-3.2.1.min.js"></script>
	<script src="https://cdn.knightlab.com/libs/timeline3/latest/js/timeline.js"></script>
</head>
<body>
    <div id='timeline-embed' style="width: 100%; height: 600px"></div>
    <script type="text/javascript">
	var timeline_json =""" + 
         JsonOutput.toJson(tsJson) + 
 """
     var options = {
		timenav_height_percentage: 50,
		marker_height_min: 40
	}
   

	window.timeline = new TL.Timeline('timeline-embed', timeline_json, options);
    </script>
    
</body>
</html>"""
    return html
}


def queryToMap(query){
    def map = [:]
    queryValues = query =~ /(\w+)=?([^&]+)?/
    queryValues[0..-1].each{
        map[it[1]] = it[2]
    }
    return map
}

def getTenantInfo(url){
    def map = [:]
    tenantValues = url =~ /(?<=https:\/\/)(.+?(?=-))(.+?(?<=\.hci[a-z][a-z][a-z].)(.+?(?=\.hana)))/
    map.id = tenantValues[0..-1][0][1]
    map.region = tenantValues[0..-1][0][3]
    map.tmnURL = "https://${map.id}-tmn.hci.${map.region}.hana.ondemand.com"
    return map
}

def renderTSJson(artefacts,hourThreshold,tenantInfo){
    def ts = [:]
    ts.title = [:]
    ts.title.media = [:]
    ts.title.text = [:]
    ts.title.media.url = "https://raw.githubusercontent.com/ambravo/tsim/master/resources/img/logo-s.png"
    ts.title.media.credit = "<a href='https://www.linkedin.com/in/arielbravo'>Ariel Bravo-Ayala</a><br>2019 - MIT License"
    ts.title.text.headline = "The Scheduled Iflows Monitor"
    ts.title.text.text = "<p>Browse your scheduled iFlows<br>An opensource project<br><br>Tenant: ${tenantInfo.id} - ${tenantInfo.region}<br>"+
                         "🔎<a href='${tenantInfo.tmnURL}/itspaces/shell/monitoring/Messages/%7B%22time%22:%22PASTHOUR%22,%22type%22:%22INTEGRATION_FLOW%22%7D' target='_blank'>Check the tenant monitor</a><p>"
    ts.events = renderTSEvents(artefacts,hourThreshold,tenantInfo)
    return ts
}

def renderTSEvents(artefacts,hourThreshold,tenantInfo){
    def events = []
    artefacts.each{ a->
        a.cronEntries.each{ ce ->
            ce.nextRuns.each{ nr ->
                def event = [:]
                event.group = a.name
                event.text = [:]
                event.background = [:]
                event.background.color = "#42075B"
                event.text.headline = "${a.name}</br>${a.version}"
                event.text.text = "Runs on: ${ce.cronDescription}"
                event.start_date = [:]
                def startDate = nr.start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                event.text.text = event.text.text + "<br>This event starts on: ${nr.start}"
                event.start_date.month = startDate.getMonthValue().toString()
                event.start_date.day = startDate.getDayOfMonth().toString()
                event.start_date.year = startDate.getYear().toString()
                event.start_date.hour = nr.start.getHours().toString()
                event.start_date.minute = nr.start.getMinutes().toString()
                if (nr.capped){
                     event.end_date = [:]
                    def endDate = nr.end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    event.end_date.month = endDate.getMonthValue().toString()
                    event.end_date.day = endDate.getDayOfMonth().toString()
                    event.end_date.year = endDate.getYear().toString()
                    event.end_date.hour = nr.end.getHours().toString()
                    event.end_date.minute = nr.end.getMinutes().toString()
                    event.background.color = "#b35322"
                    event.text.text = "Runs on: ${ce.cronDescription}<br>⚠️ This task is executed several times during the day. Consider this event as a unified reference."
                }
                event.text.text = event.text.text + "<br>🔎<a href='${tenantInfo.tmnURL}/itspaces/shell/monitoring/Messages/%7B%22artifact%22:%22${a.id}%22%7D' target='_blank'>iFlow monitor</a><p>"
                events << event
            }
        }
    }
    return events
}

def getCronEntries(bundle,now,tomorrow,hourThreshold){
    def cronEntries = []
    def cronEntrie = [:]

    bundle.findEntries("/OSGI-INF/blueprint/", "*", false).each{ url->
    BufferedReader br = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()))
        while(br.ready()){
            cron = br.readLine().find("(?<=cron=)(.+?(?=&))")
            if (cron != null){
                cronEntrie = [:]
                cronEntrie.cron = cron.replace("+"," ")
                //Description
                options = new Options()
                options.verbose = true
                cronEntrie.cronDescription = CronExpressionDescriptor.getDescription(cronEntrie.cron,options)
                cronEntrie.nextRuns = nextRuns(cronEntrie.cron,now,tomorrow,hourThreshold)
                cronEntries << cronEntrie
            }
        }
        br.close()
    }
    return cronEntries
}

def getBundleContext() {
    def knownBundleClass = 'com.sap.gateway.ip.core.customdev.util.Message'
    def entryBundle = FrameworkUtil.getBundle(Class.forName(knownBundleClass))
    if (entryBundle == null) {
        throw new AssertionError("No OSGi bundle for class ${knownBundleClass}")
    }
    entryBundle.getBundleContext()
}

def hoursBetween(start,end){
    return Duration.between(Instant.ofEpochMilli(start.getTime()), Instant.ofEpochMilli(end.getTime())).toHours() 
}

def nextRuns(cronExpression,start,end,hourThreshold){
    def cleanedList = []
    def list = []
    def dates = [:]
    def maxTotal = hourThreshold * hoursBetween(start,end)
    def currentMinDate = null

    CronTrigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)).build()
    def next = trigger.getFireTimeAfter(start)
    if (!next){return []}

    while(next && next <= end){
        dates = [:]
        dates.start = next
        next = trigger.getFireTimeAfter(next)
        dates.end = next
        dates.capped = false
        list << dates
    }
    
    if (list.size() > maxTotal){
        list.each{ l ->
            dates = [:]
            if(l.start < currentMinDate || currentMinDate == null ){
                currentMinDate  = l.start
            }
            if (l.start.getDay() != l.end.getDay()){
                dates.start = currentMinDate
                dates.end = l.end
                dates.capped = true
                cleanedList << dates
                currentMinDate = null
            }
        }
        return cleanedList
    }
    
    return list
}
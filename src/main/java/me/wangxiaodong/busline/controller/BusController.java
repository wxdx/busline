package me.wangxiaodong.busline.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;
import me.wangxiaodong.busline.util.HttpClientUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.util.*;

/**
 * ClassName:BusController
 * Package:com.json.busline.controller
 * Description:
 *
 * @Date:2018/11/14 15:58
 * @Author:hiwangxiaodong@hotmail.com
 */
@Controller
public class BusController {
    @Value("${key}")
    private String key;

    @GetMapping("/")
    public String toIndex(){
        return "index";
    }


    @PostMapping("/inputtips")
    @ResponseBody
    public Object inputTips(@RequestParam("keywords") String keywords,
                            @RequestParam(value = "city",required = false) String city){
        String param = "?output=json&key=" + key +"&keywords=" + keywords + "&city=" + city;
        String jsonResult = HttpClientUtils.doGet("http://restapi.amap.com/v3/assistant/inputtips" + param);
        System.out.println(jsonResult);
        JSONObject jsonObject = JSONObject.parseObject(jsonResult);
        Integer status = -1;
        if (jsonObject != null) {
            status = jsonObject.getInteger("status");
        }
        List<String> resultList = new ArrayList<>();
        if (status == 1){
            JSONArray tips = jsonObject.getJSONArray("tips");
            for (int i = 0; i < tips.size() ; i++) {
                JSONObject tipObject = tips.getJSONObject(i);
                String district = tipObject.getString("district");
                String name = tipObject.getString("name");
                resultList.add(district + name);
            }
        } else {
            System.out.println("调用失败");
        }
        return resultList;
    }

    @GetMapping("/query")
    public String query(@RequestParam(value = "city",required = false) String city,
                        @RequestParam(value = "origin" ,required = false) String origin,
                        @RequestParam(value = "destination",required = false) String destination, Model model){
        if (StringUtils.isEmpty(origin) || StringUtils.isEmpty(destination) ){
            return  "error";
        }
        String originLocParam = "?output=json&key=" + key +"&address=" + origin + "&city=" + city;
        String destinationLocParam = "?output=json&key=" + key +"&address=" + destination + "&city=" + city;


        String originJson = HttpClientUtils.doGet("http://restapi.amap.com/v3/geocode/geo" + originLocParam);
        String destinationJson = HttpClientUtils.doGet("http://restapi.amap.com/v3/geocode/geo" + destinationLocParam);
        JSONObject jsonObject1 = JSONObject.parseObject(originJson);
        Integer status = jsonObject1.getInteger("status");
        if (status == 1){
            JSONArray jsonArray = jsonObject1.getJSONArray("geocodes");
            for (int i = 0; i < jsonArray.size() ; i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                origin = jsonObject.getString("location");
            }
        } else {
            System.out.println("调用失败");
            return "error";
        }
        JSONObject jsonObject2 = JSONObject.parseObject(destinationJson);
        Integer status1 = jsonObject2.getInteger("status");
        if (status1 == 1){
            JSONArray jsonArray = jsonObject2.getJSONArray("geocodes");
            for (int i = 0; i < jsonArray.size() ; i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                destination = jsonObject.getString("location");
            }
        } else {
            System.out.println("调用失败");
            return "error";
        }

        //解析      api: https://lbs.amap.com/api/webservice/guide/api/direction/
        List<Object> tranPlan = new ArrayList();
        DecimalFormat df = new DecimalFormat("0.00");//设置保留位数
        String param = "?output=json&key=" + key +"&origin=" + origin + "&destination=" + destination +  "&city=" + city;
        String resultJson = HttpClientUtils.doGet("https://restapi.amap.com/v3/direction/transit/integrated" + param);
        JSONObject parseObject = JSONObject.parseObject(resultJson);
        Integer status2 = parseObject.getInteger("status");
        if (status2 == 1){
            JSONObject routeObject = parseObject.getJSONObject("route");

            //换乘列表
            JSONArray transits = routeObject.getJSONArray("transits");
            if (transits != null && transits.size() > 0){
                for (int i = 0; i < transits.size() ; i++) {
                    JSONArray jsonArray = new JSONArray();
                    JSONObject transJson = (JSONObject) transits.get(i);
                    String duration = transJson.getString("duration");
                    JSONArray segmentsArray =transJson.getJSONArray("segments");
                    if (segmentsArray != null && segmentsArray.size() > 0){
                        for (int j = 0; j < segmentsArray.size(); j++) {
                            JSONObject busJson = (JSONObject) segmentsArray.get(j);
                            JSONObject bus = (JSONObject) busJson.get("bus");
                            JSONArray buslines = bus.getJSONArray("buslines");
                            if (buslines != null && buslines.size() > 0) {
                                for (int k = 0; k < buslines.size(); k++) {
                                    JSONObject busLineInfo = (JSONObject) buslines.get(k);
                                    String departureStopName = ((JSONObject)busLineInfo.get("departure_stop")).getString("name");
                                    String arrivalStopName = ((JSONObject)busLineInfo.get("arrival_stop")).getString("name");
                                    String busName = busLineInfo.getString("name");
                                    Map<String,Object> map = new LinkedHashMap<>();
                                    JSONArray viaStops = busLineInfo.getJSONArray("via_stops");
                                    List<String> nameArray = new ArrayList<>();
                                    if (null != viaStops && viaStops.size() > 0) {
                                        for (int l = 0; l < viaStops.size(); l++) {
                                            String viaStop = ((JSONObject) viaStops.get(l)).getString("name");
                                            nameArray.add(viaStop);
                                        }
                                    }

                                    map.put("departureStopName", departureStopName);
                                    map.put("viaStops", nameArray);
                                    map.put("arrivalStopName", arrivalStopName);
                                    map.put("busName", busName);
                                    map.put("duration", df.format((float)Integer.parseInt(duration) / 60));
                                    jsonArray.add(map);
                                }
                            }
                        }
                        tranPlan.add(jsonArray);
                    }
                }
           }
        }
        model.addAttribute("tranPlan", tranPlan);
        return "detail";
//        return tranPlan;
    }
}

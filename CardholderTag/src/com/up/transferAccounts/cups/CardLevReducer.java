package com.up.transferAccounts.cups;

/*
                              _oo0oo_
                             088888880
                             88" . "88
                             (| -_- |)
	                          0\ = /0
                           ___/"---'\___
                       .' \\\\|     |// '.
                      / \\\\|||  :  |||// \\
                       /_ ||||| -:- |||||- \\
                     | | \\\\\\  -  /// |   |
                      | \_|  ''\---/''  |_/ |
                      \  .-\__  '-'  __/-.  /
                    ___'. .'  /--.--\  '. .'___
                 ."" '<  '.___\_<|>_/___.' >'  "".
                | | : '-  \'.;'\ _ /';.'/ - ' : | |
                \  \ '_.   \_ __\ /__ _/   .-' /  /
            ====='-.____'.___ \_____/___.-'____.-'=====
                              '=---='


		  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
					   佛祖保佑        iii   永无bug
*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;
import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.up.cardholderTag.consumption.cardbin.CardBinInfo;
import com.up.cardholderTag.domain.CardBinTree;
import com.up.util.Constant;
import com.up.util.MD5;
import com.up.util.TagUtility;

public class CardLevReducer extends Reducer<Text,Text,Text,Text>{
	static private Logger logger = LoggerFactory
			.getLogger(CardLevReducer.class);
	
	//private static CardBinTree globelCardBinTree = new CardBinTree();
	private static Map<String, List<CardBinInfo>> cardBinInfoMap = new HashMap<String, List<CardBinInfo>>();
	private String inCardBinPath;
	
	private String[] normal_trans_id = {"S22","D22","S20","S81","S80","D80","S21","S35","S13",
			"D13","S54","S50","D46","S51","S49","S46","S83","D56","S56","S67","S73","S71"};
	private String[] abnormal_trans_id = {"V52","V50","V81","V13","D32","S30","S75","E74","E84",
			"S84","Z99","V84","V83","V79","D33","S47","V76","S76","D76","S60","D35","V86","V73","V69"};
	private String[] cash_trans_id = {"S24","S59","S70"};						//取现相关的交易类型
	private String[] ab_cash_trans_id = {"V54"};
	private String[] cater_mcc = {"5441", "5451", "5462", "5499", "5811", "5812", "5813", "5814"};
	private String[] hotel_mcc = {"7011","7012","4511"};
	private String[] mall_mcc = {"7296", "5310", "5311", "5331", "5611", "5621", "5631", "5641", "5651", "5655", 
			"5661", "5681", "5691", "5697", "5698", "5699", "5940", "5941", "5942", "5943", "5948", "5949", "5977",
			"5997", "7210", "7211", "7216", "7230", "7251", "7295"};
	private String[] wholesale_mcc = {"5021", "5039", "5046", "5047", "5051", "5065", "5072", "5074", "5131", "5137", 
			"5172", "5193", "5998","4225", "4458", "4468", "5013", "5044", "5045", "5111", "5122", "5139", "5192", 
			"5198", "5398"};
	
	
	@Override
	protected void setup(Context context) throws IOException,
	InterruptedException {
		
		Configuration conf = context.getConfiguration();
		
		// 卡bin表预读入
		inCardBinPath = conf.get("inCardBinPath").trim();
		if (inCardBinPath.substring(inCardBinPath.length() - 1).equals("/")) {
			inCardBinPath = inCardBinPath + "part-r-00000";
		} else {
			inCardBinPath = inCardBinPath + "/part-r-00000";
		}
		
		List<CardBinInfo> cardBinList = null;
		
		FileSystem fs = FileSystem.get(conf);
		FSDataInputStream hdfsInStream = fs.open(new Path(inCardBinPath));
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				hdfsInStream, "UTF-8"));
		String tmpString = null;
		while ((tmpString = reader.readLine()) != null) {
			CardBinInfo cardBinInfo = new CardBinInfo();

			String[] cardInfoBin = tmpString.split("\t");
			cardBinInfo.setCardBinTp(cardInfoBin[0]);
			cardBinInfo.setCardBin(cardInfoBin[1]);
			cardBinInfo.setCardLenStart(Integer.parseInt(cardInfoBin[2]));
			cardBinInfo.setCardLen(Integer.parseInt(cardInfoBin[3]));
			cardBinInfo.setCardBegin(Long.parseLong(cardInfoBin[4]));
			cardBinInfo.setCardEnd(Long.parseLong(cardInfoBin[5]));
			cardBinInfo.setCardLvl(cardInfoBin[6]);
			
			if (cardBinInfoMap.containsKey(cardBinInfo.getCardBin())) {
				cardBinList = cardBinInfoMap.get(cardBinInfo.getCardBin());
			} else {
				cardBinList = new ArrayList<CardBinInfo>();
			}
			cardBinList.add(cardBinInfo);
			
			cardBinInfoMap.remove(cardBinInfo.getCardBin());
			cardBinInfoMap.put(cardBinInfo.getCardBin(), cardBinList);
		}
	}
	

	
	public void reduce(Text key, Iterable<Text> lines, Context context) throws IOException, InterruptedException{
		String[] keyTokens = key.toString().split(Constant.separator_1);
		HmacMd5 rand=new HmacMd5();
		
		logger.info("============= new version ===============");
		String card_num = keyTokens[0].toString();
//		String card_md5 = MD5.GetMD5Code(card_num);						//MD5
		String card_md5 = "";
		try {
			card_md5 = rand.evaluate(card_num).toString();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String iss_ins_code = ""					;					//发卡机构代码
		String iss_ins_name = "";										//发卡机构名称
		String card_level = "-2";										//卡等级
		String platinumCardFlg = "0";									// 白金卡卡号标识
		String cardBin = "";								 			// 卡bin		10

		//=============================== 计算开始 ========================================
		StringBuilder sb = new StringBuilder();
		List<CardBinInfo> cardBinList = null;
		CardBinInfo cardBininfo = null;
		int cardLenStart;
		int cardLen;
		long cardBegin;
		long cardEnd;
		Long cardNoSubStr;

		int count = 0;
		for(Text line: lines){
			String[] tokens = line.toString().split(Constant.separator_1);
			//判断白金卡
			if (tokens.length == 1) {
				platinumCardFlg = "1";
			}else{
				count++;
				if(count>2000)
					break;
			}
		}
		
		//计算卡等级
		if ("1".equals(platinumCardFlg)) {
			card_level = "白金卡";
		} else {
			card_level = "普卡";
			if (cardBin.length() >= 8 && card_num.length()>8) {
				if (cardBinInfoMap.containsKey(cardBin.substring(0, 8))) {
					cardBinList = cardBinInfoMap.get(cardBin.substring(0, 8));
					for (int i = 0; i < cardBinList.size(); i++) {
						cardBininfo = cardBinList.get(i);
						cardLenStart = cardBininfo.getCardLenStart() - 1;
						cardLen = cardBininfo.getCardLen();
						cardBegin = cardBininfo.getCardBegin();
						cardEnd = cardBininfo.getCardEnd();
						//System.out.println("=======================:"+card_num);
						//System.out.println("=======================:"+cardLenStart);
						//System.out.println("=======================:"+ (cardLenStart + cardLen));
						cardNoSubStr = Long.valueOf(card_num.substring(cardLenStart,
								cardLenStart + cardLen));
						if (cardNoSubStr >= cardBegin && cardNoSubStr <= cardEnd) {
							card_level = cardBininfo.getCardLvl();
						}
					}
				}
			}
		}
		
		
		sb.append(card_md5+Constant.separator_1);									//MD5
		sb.append(card_level);	

		context.write(new Text(sb.toString()), new Text(""));
	}

	class TransItem{
		
		private String cardNum;						//卡号
		private String md5;							//MD5
		private String iss_ins_code;				//发卡机构代码
		private String mcc;							//mcc
		private String mccName;						//mcc名称
		private String mchntName;					//商户名
		private String time;						//消费时间
		private String year;						//年
		private String month;						//月份
		private double  trans_at = 0;						//消费金额
		private String acptIns;						//受理机构
		private String locationCd;					//地区代码
		private String city;						//城市
		private String province;					//省份
		private String realProvince;				//真实省份
		
		private double  rcv_settle_at;				//清算金额
		private String card_bin;					//卡bin
		private String trans_id;					//交易类型
		private String fwd_ins_id_cd;				//发送机构标识码
	
		public TransItem(String cardNum, String md5, String mcc, String mccName,
				String mchntName, String time, double  trans_at, String acptIns) {
			super();
			this.cardNum = cardNum;
			this.md5 = md5;
			this.mcc = mcc;
			this.mccName = mccName;
			this.mchntName = mchntName;
			this.time = time;
			this.trans_at = trans_at/100;
			this.acptIns = acptIns;
		}
		
		public TransItem(String line){
			String[] tokens = line.split(",");
			this.cardNum = tokens[0].trim();
			this.md5 = tokens[1].trim();
			this.iss_ins_code = tokens[6].trim();
			this.mcc = tokens[11].trim();
			this.mccName = tokens[18].trim();
			this.mchntName = tokens[13].trim();
			this.time = tokens[8].trim().substring(0,14);
			
			//System.out.println("=========:"+time);
			//this.year = time.substring(0,4);
			this.year = "";
			this.month = time.substring(0,6);                           // 例：201501 ++++++++++++++
			
			if(!tokens[15].trim().equals("") || !tokens[15].trim().equals(null))
				this.trans_at = Double.parseDouble (tokens[15].trim())/100;
			this.acptIns = tokens[7].trim();
			this.locationCd = this.acptIns.substring(4,8);				//地区代码
			this.city = Constant.getCity(this.locationCd);
			this.province = Constant.getProvince(this.city);
			this.realProvince = Constant.getCityKey(this.mchntName);
			
			if(!tokens[24].trim().equals("") || !tokens[24].trim().equals(null))
				this.rcv_settle_at = Double.parseDouble (tokens[24].trim())/100;
			this.card_bin = tokens[16].trim();
			this.trans_id = tokens[17].trim();
			this.fwd_ins_id_cd = tokens[22].trim();
			
			//修正异地收单商户的所在地
			/*
			if(!realProvince.equals("null")){
				if(!realProvince.equals(province))
					this.province = this.realProvince;
			}*/
			if(realProvince.equals("null"))
				this.realProvince = this.province;
		}

		public String getCardNum() {
			return cardNum;
		}

		public void setCardNum(String cardNum) {
			this.cardNum = cardNum;
		}
		
		public String getIss_ins_code() {
			return iss_ins_code;
		}

		public void setIss_ins_code(String iss_ins_code) {
			this.iss_ins_code = iss_ins_code;
		}

		public String getMd5() {
			return md5;
		}

		public void setMd5(String md5) {
			this.md5 = md5;
		}

		public String getMcc() {
			return mcc;
		}

		public void setMcc(String mcc) {
			this.mcc = mcc;
		}

		public String getMccName() {
			return mccName;
		}

		public void setMccName(String mccName) {
			this.mccName = mccName;
		}

		public String getMchntName() {
			return mchntName;
		}

		public void setMchntName(String mchntName) {
			this.mchntName = mchntName;
		}

		public String getTime() {
			return time;
		}

		public void setTime(String time) {
			this.time = time;
		}
		
		public String getYear() {
			return year;
		}

		public void setYear(String year) {
			this.year = year;
		}

		public String getMonth() {
			return month;
		};

		public void setMonth(String month) {
			this.month = month;
		}

		public double  getTrans_at() {
			return trans_at;
		}

		public void setTrans_at(double  trans_at) {
			this.trans_at = trans_at;
		}
		
		public String getAcptIns() {
			return acptIns;
		}

		public void setAcptIns(String acptIns) {
			this.acptIns = acptIns;
		}
		
		public void add(TransItem item){
			this.trans_at += item.trans_at;
		}
		
		public String getLocationCd() {
			return locationCd;
		}

		public void setLocationCd(String locationCd) {
			this.locationCd = locationCd;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getProvince() {
			return province;
		}

		public void setProvince(String province) {
			this.province = province;
		}

		public String getRealProvince() {
			return realProvince;
		}

		public void setRealProvince(String realProvince) {
			this.realProvince = realProvince;
		}

		public double  getRcv_settle_at() {
			return rcv_settle_at;
		}

		public void setRcv_settle_at(double  rcv_settle_at) {
			this.rcv_settle_at = rcv_settle_at;
		}

		public String getCard_bin() {
			return card_bin;
		}

		public void setCard_bin(String card_bin) {
			this.card_bin = card_bin;
		}

		public String getTrans_id() {
			return trans_id;
		}

		public void setTrans_id(String trans_id) {
			this.trans_id = trans_id;
		}

		public String getFwd_ins_id_cd() {
			return fwd_ins_id_cd;
		}

		public void setFwd_ins_id_cd(String fwd_ins_id_cd) {
			this.fwd_ins_id_cd = fwd_ins_id_cd;
		}

		//比较两个时间差
		public long getTimeDistance(TransItem item) throws Exception{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
			Date date1 = sdf.parse(this.getTime());
			Date date2 = sdf.parse(item.getTime());
			return Math.abs((date1.getTime()-date2.getTime()))/(1000*60);
		}
		
		//判断两条记录是否属于同一笔交易
		public boolean isSameTrans(TransItem item) throws Exception{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
			if(this.mchntName.equals(item.mchntName)){
				if(this.getTimeDistance(item)<60)
					return true;
			}
			return false;
		}

		public String toString(){
			StringBuilder sb = new StringBuilder();
			sb.append(this.time+Constant.separator_3);
			sb.append(this.mcc+Constant.separator_3);
			sb.append(this.mchntName+Constant.separator_3);
			sb.append(this.acptIns+Constant.separator_3);
			sb.append(this.trans_at);
			return sb.toString();
		}
	}
	
	
	/**
	 *  去除队列里的负值
	 * */
	private void removeNagetive(ArrayList<Double> list){
		for(int i=0; i<list.size(); i++){
			if(list.get(i)<=0)
				list.remove(i);
		}
	}
	
	private int getTrxAmtScore(double amount){
		if(amount>=0 && amount<890)
			return 10;
		else if(amount>=890 && amount<2400)
			return 20;
		else if(amount>=2400 && amount<4500)
			return 30;
		else if(amount>=4500 && amount<7300)
			return 40;
		else if(amount>=7300 && amount<11200)
			return 50;
		else if(amount>=11200 && amount<17200)
			return 60;
		else if(amount>=17200 && amount<26800)
			return 70;
		else if(amount>=26800 && amount<44400)
			return 80;
		else if(amount>=44400 && amount<85000)
			return 90;
		else if(amount>=85000)
			return 100;
		else
			return 10;
	}
	
	private int getTrxUnitScore(int count){
		if(count>=0 && count<3)
			return 10;
		else if(count>=3 && count<5)
			return 20;
		else if(count>=5 && count<7)
			return 30;
		else if(count>=7 && count<10)
			return 40;
		else if(count>=10 && count<13)
			return 50;
		else if(count>=13 && count<16)
			return 60;
		else if(count>=16 && count<21)
			return 70;
		else if(count>=21 && count<28)
			return 80;
		else if(count>=28 && count<42)
			return 90;
		else if(count>=42)
			return 100;
		else
			return 10;
	}
	
	private int getCashScore(int flag){
		if(flag>0)
			return 0;
		else
			return 100;
	}
	
	private int getCaterScore(int flag){
		if(flag>0)
			return 100;
		else
			return 0;
	}
	
	private int getHotelScore(int count){
		if(count==1)
			return 80;
		else if(count==2)
			return 90;
		else if(count>=3)
			return 100;
		else
			return 10;
	}
	
	private int getMallScore(int count){
		if(count==1)
			return 50;
		else if(count==2)
			return 70;
		else if(count>=3 && count<5)
			return 80;
		else if(count>=5 && count<8)
			return 90;
		else if(count>=8)
			return 100;
		else
			return 10;
	}
	
	private int getWholesaleScore(int count){
		if(count==1)
			return 60;
		else if(count==2)
			return 70;
		else if(count==3)
			return 80;
		else if(count>=4 && count<6)
			return 90;
		else if(count >= 6)
			return 100;
		else
			return 10;
	}
	
	private int getMonthScore(int count){
		if(count<2)
			return 5;
		else if(count==2)
			return 10;
		else if(count==3)
			return 15;
		else if(count==4)
			return 20;
		else if(count==5)
			return 25;
		else 
			return 35;
	}
	
	private int getVarScore(double var){
		if(var>=0 && var<148)
			return 43;
		else if(var>=148 && var<316)
			return 25;
		else if(var>=316)
			return 15;
		else
			return 0;
	}
	
	private int getCardLelScore(String level){
		if(level.equals("普卡"))
			return 30;
		else if(level.equals("金卡"))
			return 40;
		else if(level.equals("白金卡"))
			return 50;
		else if(level.equals("钻石卡"))
			return 80;
		return 0;
	}
	
	/**
	 * 计算标准差
	 * 
	 * */
	private String getStandardDevition(ArrayList<Double> list){
		DecimalFormat df = new DecimalFormat("######0.0000"); 
		if(list.size()>0)
		{
			double  sum = 0f;
			
			for(double  elem : list)
				sum += elem;
			double  mean = sum/list.size();				//均值
			
			sum = 0f;
			for(double elem : list)
			{
				sum += (elem-mean)*(elem-mean);
			}
			
			return df.format(100*Math.sqrt(sum/list.size()));
		}
		else
			return df.format(0);
	}
	
	/**
	 * 判断是否是普通交易类型（消费、预授权。。。）
	 * 
	 * */
	private boolean isNormalTransId(String trans_id){
		for(int i=0 ; i<normal_trans_id.length; i++){
			if(trans_id.equals(normal_trans_id[i]))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断是否是非普通交易类型（冲正、撤销。。。）
	 * 
	 * */
	private boolean isAbnormalTransId(String trans_id){
		for(int i=0 ; i<abnormal_trans_id.length; i++){
			if(trans_id.equals(abnormal_trans_id[i]))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断是否是正常取现类交易类型
	 * 
	 * */
	private boolean isCashTransId(String trans_id){
		for(int i=0 ; i<cash_trans_id.length; i++){
			if(trans_id.equals(cash_trans_id[i]))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断是否是取现类撤销交易类型
	 * 
	 * */
	private boolean isAbCashTransId(String trans_id){
		for(int i=0 ; i<ab_cash_trans_id.length; i++){
			if(trans_id.equals(ab_cash_trans_id[i]))
				return true;
		}
		return false;
	}
	
	
	/**
	 * 判断是否餐饮类交易
	 * 
	 * */
	private boolean isCaterMcc(String mcc){
		for(int i=0 ; i<cater_mcc.length; i++){
			if(mcc.equals(cater_mcc[i]))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断是否宾馆航空类交易
	 * 
	 * */
	private boolean isHotelMcc(String mcc){
		for(int i=0 ; i<hotel_mcc.length; i++){
			if(mcc.equals(hotel_mcc[i]))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断是否日用百货类交易
	 * 
	 * */
	private boolean isMallMcc(String mcc){
		for(int i=0 ; i<mall_mcc.length; i++){
			if(mcc.equals(mall_mcc[i]))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断是否批发类交易
	 * 
	 * */
	private boolean isWholesaleMcc(String mcc){
		for(int i=0 ; i<wholesale_mcc.length; i++){
			if(mcc.equals(wholesale_mcc[i]))
				return true;
		}
		return false;
	}

	/**
	 * 计算当前时刻属于哪个时段
	 * */
	public String getTimeIntervelName(String hour){
		int str = Integer.parseInt(hour);
		for(int i=TagUtility.TIME_INTERVAL.length-1; i >0 ; i--){
			if(str>=TagUtility.TIME_INTERVAL[i-1] && str<TagUtility.TIME_INTERVAL[i]){
				return TagUtility.TIME_INTERVAL_NAME[i];
			}
		}
		return TagUtility.TIME_INTERVAL_NAME[0];
	}
	
	/**
	 * 将哈希表里的次数数据 连成记录，存在stringBuilder里，并返回次数最大的key
	 * */
	public String getRecordFromMap(HashMap<String, Integer> map, StringBuilder sb){
		int max = 0;
		String maxString = "";
		Iterator it = map.keySet().iterator();
		while(it.hasNext()){
			String key = (String)it.next();
			int count = map.get(key);
			if(count>max){
				max = count;
				maxString = key;
			}
			sb.append(key+Constant.separator_3+count);
			sb.append(Constant.separator_2);
		}
		sb.setLength(sb.length()-Constant.separator_2.length());
		return maxString;
	}
	
	/**
	 * 计算两个日期之间的时间间隔（天）
	 * */
	public static int getDayInterval(SimpleDateFormat sdf, String time1, String time2){
		Date date1;
		Date date2;
		try {
			date1 = sdf.parse(time1);
			date2 = sdf.parse(time2);
			return (int)Math.abs((date1.getTime()-date2.getTime()))/(1000*60*60*24);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	/**
	 * 计算当前的消费金额属于哪个区间
	 * 0-200，200-300，300-400，400-500，500-600，600-700，700-800，800-900，900-1000，1000+
	 * */
	public String getAmountInterval(double  amount){
		if(amount>TagUtility.AMOUNT_INTERVAL[TagUtility.AMOUNT_INTERVAL.length-1])
			return TagUtility.AMOUNT_INTERVAL_NAME[TagUtility.AMOUNT_INTERVAL_NAME.length-1];
		for(int i = TagUtility.AMOUNT_INTERVAL.length-1; i>0; i--)
		{
			if(amount>=TagUtility.AMOUNT_INTERVAL[i-1] && amount<TagUtility.AMOUNT_INTERVAL[i])
				return TagUtility.AMOUNT_INTERVAL_NAME[i];
		}	
		
		return TagUtility.AMOUNT_INTERVAL_NAME[0];
	}
	
//	public static void main(String args[]){
//		ArrayList<Double> monthAmntList = new ArrayList<Double>();
//		ArrayList<Double> monthLiftList = new ArrayList<Double>();
//		String T_Trx_var_L6M = "99999999";
//		ConsumptionReducer app = new ConsumptionReducer();
//		monthAmntList.add(2342.2);
//		monthAmntList.add(1234.2);
//		monthAmntList.add(4654.1);
//		monthAmntList.add(3789.3);
//		monthAmntList.add(7344.2);
//		
//		if(monthAmntList.size()<3)
//			T_Trx_var_L6M = "99999999";
//		else{
//			for(int i=1; i<monthAmntList.size(); i++){
//				monthLiftList.add(Math.log(monthAmntList.get(i)/monthAmntList.get(i-1)));
//			}
//			T_Trx_var_L6M = app.getStandardDevition(monthLiftList);
//		}
//		System.out.println(app.getVarScore(Double.parseDouble(T_Trx_var_L6M)));
//	}
}
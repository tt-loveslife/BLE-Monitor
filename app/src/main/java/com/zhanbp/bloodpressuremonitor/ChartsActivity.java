package com.zhanbp.bloodpressuremonitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.aware.Characteristics;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HeaderViewListAdapter;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.blakequ.bluetooth_manager_lib.BleManager;
import com.blakequ.bluetooth_manager_lib.BleParamsOptions;
import com.blakequ.bluetooth_manager_lib.connect.BluetoothConnectInterface;
import com.blakequ.bluetooth_manager_lib.connect.BluetoothSubScribeData;
import com.blakequ.bluetooth_manager_lib.connect.ConnectConfig;
import com.blakequ.bluetooth_manager_lib.connect.ConnectState;
import com.blakequ.bluetooth_manager_lib.connect.ConnectStateListener;
import com.blakequ.bluetooth_manager_lib.connect.GattError;
import com.blakequ.bluetooth_manager_lib.connect.multiple.MultiConnectManager;
import com.blakequ.bluetooth_manager_lib.device.resolvers.GattAttributeResolver;
import com.leon.lfilepickerlibrary.LFilePicker;
import com.orhanobut.logger.Logger;


import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import de.greenrobot.event.EventBus;



public class ChartsActivity extends Activity implements View.OnClickListener {    //再加个文本框平均压显示

	private static final String TAG = "message";

	/*组件相关*/
	private LinearLayout layout_chart1;
	private Button startbutton1;
	private Button inflatebutton;
	private Button stopbutton;
	private Button savebutton;

	/* 文本框内容 */
	private TextView BLE;
	private TextView BLE1_offset;
	private TextView BLE2_offset;

	/* 定时器 和 定时任务 */
	private Timer timer = new Timer();
	private TimerTask task;
	private TimerTask taskForCurve;
	private TimerTask taskForInflate;

	private ProgressDialog progressDialog;

	/*图表相关*/
	private Handler handler;	//渲染器
	private Handler curveValueHandler;
	private Handler inflateHandler;
	private GraphicalView chart1;
	private XYMultipleSeriesRenderer renderer1;
	private XYMultipleSeriesDataset dataset1;	//一个图的数据组
	private TimeSeries series11;	//纵轴数据
	private TimeSeries series12;
	private TimeSeries series13;
	private TimeSeries seriesDC1;	//横轴数据
	private TimeSeries seriesDC2;
	private TimeSeries seriesDC3;

	/*数据相关*/
	boolean CONNECT_STATA;//连接状态
	boolean RECORD_STATA;//数据保存状态
	boolean mearsure_flag = false;//开始测量标志位

	/* 血压重启设备、血氧接受设备工作指令 */
	private final byte[] BLOODSTART = {(byte) 0XFA, (byte) 0XAA, (byte) 0XAA, (byte) 0XAF,(byte) 0X00,(byte) 0X0A,(byte) 0X10,(byte) 0X1A, (byte) 0XF5,(byte) 0X5F};
	private final byte[] BLOODSTOP =  {(byte) 0XFA, (byte) 0XAA, (byte) 0XAA, (byte) 0XAF,(byte) 0X00,(byte) 0X0A,(byte) 0X11,(byte) 0X1B, (byte) 0XF5,(byte) 0X5F};

	/* 三个设备的物理Mac地址 */
	private final String BLOODPRESSUREADDR = "E7:4E:AD:39:EC:EC";
	private final String GASPRESSUREADDR = "CA:CE:1D:C0:97:C8";
	private final String CUFFPRESSUREADDR = "74:8B:34:00:09:0D";
	/* 蓝牙数据接受容器 */
	Long[] BP_DATA_BUFFER = new Long[2];
	Long[] GP_DATA_BUFFER = new Long[2];
	Long[] CP_DATA_BUFFER = new Long[2];
	int[] ycache = new int[201];	//Y缓存，更新用

	/* Excel 图表列标题 */
	private String[] title2 = {"时间","袖带压力"};
	private String[] title1 = {"时间","气箱压力"};
	private String[] title0 = {"时间","血压"};

	/* 数据DAO容器 */
	private ArrayList<ArrayList<Long>> CP_Record_Data;//数据集,保存记录用(0)时间 (1)压力值
	private ArrayList<ArrayList<Long>> BP_Record_Data;//数据集,保存记录用(0)时间 (1)压力值
	private ArrayList<ArrayList<Long>> GP_Record_Data;//数据集,保存记录用(0)时间 (1)压力值

	/* 保存Excel相关信息 */
	private String ExcelName;

	/* 信号处理相关 */
	boolean startrecord_flag = false;

	/* 蓝牙相关 */
	MultiConnectManager multiConnectManager;  //多设备连接
	private BluetoothAdapter bluetoothAdapter;   //蓝牙适配器
	private ArrayList<String> connectDeviceMacList; //需要连接的mac设备集合
	private ArrayList<String> connectDeviceNameList; //需要连接的mac设备集合
	private BluetoothGattCharacteristic notifyCharacteristic;
	private BluetoothGattCharacteristic writeCharacteristic;
	ArrayList<BluetoothGatt> gattArrayList; //设备gatt集合
	HashMap<BluetoothGatt, BluetoothGattCharacteristic> bluetoothHashMap;
	private final int REQUEST_CODE_PERMISSION = 1; // 权限请求码  用于回调

	public ChartsActivity() {
	}

	private class DATALOADER extends AsyncTask<Void, Void, int[][]> {
		protected void onPreExecute() {
			ChartsActivity.this.progressDialog.setMessage("加载中,请稍后......");
			ChartsActivity.this.progressDialog.setCanceledOnTouchOutside(false);
			ChartsActivity.this.progressDialog.show();
			Log.i("here", "total cols is ");
		}

		protected int[][] doInBackground(Void[] paramArrayOfVoid) {
			ExcelIO EIO = new ExcelIO(ChartsActivity.this);
			return EIO.getXlsData("Pressure3.xls", 0);
		}

		protected void onPostExecute(int[][] paramList) {
			if ((ChartsActivity.this.progressDialog != null) && (ChartsActivity.this.progressDialog.isShowing()))
				ChartsActivity.this.progressDialog.dismiss();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chart);
		// 变量、表格初始化
		initVariables();
		Intent ConnectDevices = getIntent();
		connectDeviceMacList = ConnectDevices.getStringArrayListExtra("connectMAC");
		connectDeviceNameList = ConnectDevices.getStringArrayListExtra("connectNAME");
		initView();
		initEvent();
		initRenderandDataset();
		initChart();
		requestWritePermission();
		initBLEConfig();
		EventBus.getDefault().register(this);//   ②注册事件
		ProgressDialog localProgressDialog = new ProgressDialog(this);
		this.progressDialog = localProgressDialog;
		new DATALOADER().execute();
		initTimerTask();
		// 连接蓝牙
		connentBluetooth();
	}

	/**
	 * 初始化变量，变量服务于：蓝牙连接模块 、 图表模块
	 */
	private void initVariables() {
		connectDeviceMacList = new ArrayList<>();
		gattArrayList = new ArrayList<>();
		bluetoothHashMap = new HashMap<>();
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();//bluetoothAdapter的初始化
		// 初始化蓝牙连接状态及相关变量
		CONNECT_STATA = false;
		RECORD_STATA = false;
		mearsure_flag = false;
		for (int j = 0; j < BP_DATA_BUFFER.length; j++) {
			BP_DATA_BUFFER[j] = Long.valueOf(0);
		}
		BP_Record_Data = new ArrayList<>();
		for (int j = 0; j < GP_DATA_BUFFER.length; j++) {
			GP_DATA_BUFFER[j] = Long.valueOf(0);
		}
		GP_Record_Data = new ArrayList<>();
		for (int j = 0; j < CP_DATA_BUFFER.length; j++) {
			CP_DATA_BUFFER[j] = Long.valueOf(0);
		}
		CP_Record_Data = new ArrayList<>();
		seriesDC1 = new TimeSeries("time1");
		seriesDC2 = new TimeSeries("time2");
		seriesDC3 = new TimeSeries("time3");
		int nr = 201;                                        //200个数
		for (int k = 0; k < nr; k++) {
			seriesDC1.add(k, 0);    //初始化为0
			seriesDC2.add(k, 0);
			seriesDC3.add(k, 0);
		}
	}

	/**
	 * 初始化页面视图，视图包括：按钮、 蓝牙配件
	 */
	private void initView() {
		// 初始化图表
		layout_chart1 = (LinearLayout) findViewById(R.id.linearlayout_chart1);
		// 初始化按钮
		savebutton = (Button) this.findViewById(R.id.saveButton1);
		inflatebutton = (Button) this.findViewById(R.id.inflateButton1);
		startbutton1 = (Button) this.findViewById(R.id.startButton1);
		stopbutton = (Button) this.findViewById(R.id.stopButton1);
		// 初始化曲线值
		BLE1_offset = (TextView) this.findViewById(R.id.ble1_offset);
		BLE2_offset = (TextView) this.findViewById(R.id.ble2_offset);
		// 初始化蓝牙配件
		BLE = (TextView) this.findViewById(R.id.ble1);
		for (int i = 0; i < connectDeviceNameList.size(); i++) {
			BLE.setText(connectDeviceNameList.get(i));
		}
	}

	/**
	 * 初始化按钮事件
	 */
	private void initEvent() {
		// 唤醒按钮事件处理
		startbutton1.setOnClickListener(this);
		stopbutton.setOnClickListener(this);
		savebutton.setOnClickListener(this);
		inflatebutton.setOnClickListener(this);
	}

	/**
	 * 初始化横坐标曲线
	 */
	private void initRenderandDataset() {
		renderer1 = getDemoRenderer(new XYMultipleSeriesRenderer(3));
		series11 = new TimeSeries("BP");
		series12 = new TimeSeries("GP");
		series13 = new TimeSeries("CP");
		dataset1 = getDateDemoDataset(new XYMultipleSeriesDataset(), series11, series12, series13);
	}

	/**
	 * 初始化图表
	 */
	private void initChart() {
		chart1 = ChartFactory.getLineChartView(this, dataset1, renderer1);
		layout_chart1.addView(chart1, new LayoutParams(LayoutParams.WRAP_CONTENT, 600));
	}

	/**
	 * 初始化图表曲线周期更新和曲线数值周期更新任务
	 */
	private void initTimerTask(){
		// 延时 1ms 每隔 100ms刷新一次图表
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				// 确认连接成功后，刷新数据
				if (CONNECT_STATA) {
					updateChart(chart1,dataset1,series11,series12,series13,BP_DATA_BUFFER[1],GP_DATA_BUFFER[1], CP_DATA_BUFFER[1]);
				}
				super.handleMessage(msg);
			}
		};
		task = new TimerTask() {
			@Override
			public void run() {
				Message message = new Message();
				message.what = 200;//用户自定义码,ID
				handler.sendMessage(message);
			}
		};
		timer.schedule(task, 1, 200);//p1：要操作的方法，p2：要设定延迟的时间，p3：周期的设定（ms单位）

		// 延时 1ms 每隔500ms刷新一次曲线值
		curveValueHandler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				// 确认连接成功后，刷新数据
				if (CONNECT_STATA) {
					BLE1_offset.setText("BP: " + BP_DATA_BUFFER[1]);
					BLE2_offset.setText("SpO2: " + GP_DATA_BUFFER[1]);
				}
				super.handleMessage(msg);
			}
		};
		taskForCurve = new TimerTask() {
			@Override
			public void run() {
				Message message = new Message();
				message.what = 200;//用户自定义码,ID
				curveValueHandler.sendMessage(message);
			}
		};
		timer.schedule(taskForCurve, 1, 500);//p1：要操作的方法，p2：要设定延迟的时间，p3：周期的设定（ms单位）

		// 延时充气： 点击后 1min 开始充气
		inflateHandler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				// 确认连接成功后，刷新数据
				startInflating();
				super.handleMessage(msg);
			}
		};
		taskForInflate = new TimerTask() {
			@Override
			public void run() {
				Message message = new Message();
				message.what = 200;//用户自定义码,ID
				inflateHandler.sendMessage(message);
			}
		};
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			/*开始记录*/
			case R.id.startButton1:
				//将数据清零，重新开始记录
				BP_Record_Data.clear();
				GP_Record_Data.clear();
				CP_Record_Data.clear();
				RECORD_STATA = true;//记录数据标志位
				savebutton.setText(this.getString(R.string.SaveData));//重置保存按钮
				break;

			/* 延时充气测量 */
			case R.id.inflateButton1:
				// 向 健拓设备发送开始测量信号
				try{
					timer.schedule(taskForInflate, 60 * 1000);
				}catch (Exception e){
					e.printStackTrace();
				}
				//将数据清零，重新开始记录
				BP_Record_Data.clear();
				GP_Record_Data.clear();
				CP_Record_Data.clear();
				RECORD_STATA = true;//记录数据标志位
				savebutton.setText(this.getString(R.string.SaveData));//重置保存按钮
				break;

			/*停止记录*/
			case R.id.stopButton1:
				stopBloodAndSPO2();
				if (startrecord_flag && (!BP_Record_Data.isEmpty() || !GP_Record_Data.isEmpty() || !CP_Record_Data.isEmpty())) {
					RECORD_STATA = false;
					savebutton.setText(this.getString(R.string.preSaveData));//提示保存数据
				}
				break;

			/*保存数据*/
			case R.id.saveButton1:
				if (!BP_Record_Data.isEmpty() || !GP_Record_Data.isEmpty() || !CP_Record_Data.isEmpty()) {
					setExcelNameAndSave();//弹窗取名//保存
				} else {
					Toast.makeText(this, "未记录任何数据", Toast.LENGTH_LONG).show();
				}
				break;
		}
	}

	/**
	 * 编辑保存的Excel名称
	 */
	private void setExcelNameAndSave() {
		final EditText setExcelName = new EditText(this);
		new AlertDialog.Builder(this)
				.setTitle("请输入文件名")
				.setView(setExcelName)
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						//按下确定键后的事件
						Toast.makeText(getApplicationContext(), setExcelName.getText().toString(), Toast.LENGTH_LONG).show();
						ExcelName = "Sensor_BP" + "_" + setExcelName.getText().toString();
						saveData();
					}
				})
				.setNegativeButton("取消", null).show();
	}

	/**
	 * 将蓝牙传输上来的数据保存至Excel
	 */
	private void saveData() {
		File externalStoreage = Environment.getExternalStorageDirectory();
		String externalStoragePath = externalStoreage.getAbsolutePath();//获取存储位置
		String directory = externalStoragePath + File.separator + "BloodPressure";//创建路径
		File filepath = new File(directory);
		if (!filepath.exists()) {
			filepath.mkdirs();
		}
		String fileName = ExcelName + ".xls";
		File xlsFile = new File(filepath, fileName);//然后再创建文件的File对象    //文件和文件夹必须分开创建！
		String absolutePath = xlsFile.getAbsolutePath();
		if (absolutePath != null) {
			ExcelIO.initExcel(xlsFile, absolutePath, title0, title1, title2);//
			ExcelIO.writeObjListToExcel(BP_Record_Data,absolutePath, this, 0);
			ExcelIO.writeObjListToExcel(GP_Record_Data, absolutePath, this, 1);
			ExcelIO.writeObjListToExcel(CP_Record_Data, absolutePath, this, 2);
			savebutton.setText(this.getString(R.string.SaveData));//重置保存按钮
		}
	}

	/**
	 *	由定时器每隔100ms触发一次的更新图表函数，上面最多可能有4条曲线
	 * @param chart:图表对象
	 * @param Dataset：数据集对象
	 * @param series1：BP曲线对象
	 * @param series2：SPO2曲线对象
	 * @param addYY1：添加的一个数据
	 * @param addYY2：添加的一个数据
	 */
	private void updateChart(GraphicalView chart, XYMultipleSeriesDataset Dataset, TimeSeries series1, TimeSeries series2, TimeSeries series3, long addYY1, long addYY2, long addYY3) {
		boolean s1 = false;
		int length = series1.getItemCount();
		int length2 = series2.getItemCount();
		int length3 = series3.getItemCount();

		if (length >= 201) length = 201;
		if (length2 >= 201) length2 = 201;
		if (length3 >= 201) length2 = 201;

		for (int i = 0; i < length; i++) {                //数据存入缓存
			ycache[i] = (int) series1.getY(i);
		}
		series1.clear();                                //清除数据
		for (int k = 0; k < length - 1; k++) {                //加入缓存中的数据
			series1.add(k, ycache[k + 1]);
		}
		series1.add(length - 1, addYY1);                //加入新数据
		///////////////////////////////////
		for (int i = 0; i < length2; i++) {                //数据存入缓存
			ycache[i] = (int) series2.getY(i);
		}
		series2.clear();                                //清除数据
		for (int k = 0; k < length2 - 1; k++) {                //加入缓存中的数据
			series2.add(k, ycache[k + 1]);
		}
		series2.add(length2 - 1, addYY2);                //加入新数据
		///////////////////////////////////
		for (int i = 0; i < length3; i++) {                //数据存入缓存
			ycache[i] = (int) series3.getY(i);
		}
		series3.clear();                                //清除数据
		for (int k = 0; k < length3 - 1; k++) {                //加入缓存中的数据
			series3.add(k, ycache[k + 1]);
		}
		series3.add(length3 - 1, addYY3);                //加入新数据
		Dataset.removeSeries(series1);                //更新数据组
		Dataset.addSeries(series1);
		Dataset.removeSeries(series2);
		Dataset.addSeries(series2);
		Dataset.removeSeries(series3);
		Dataset.addSeries(series3);
		chart.invalidate();
	}

	/**
	 * 设置图表的属性
	 * @param renderer
	 * @return
	 */
	private XYMultipleSeriesRenderer getDemoRenderer(XYMultipleSeriesRenderer renderer) {
		renderer.setChartTitle("BP Monitor");//标题
		renderer.setChartTitleTextSize(45);
		renderer.setXTitle("Time");    //X标签
		renderer.setAxisTitleTextSize(20);//轴标签大小
		renderer.setAxesColor(Color.WHITE);//轴颜色
		renderer.setLabelsTextSize(20);    //轴刻度大小
		renderer.setLabelsColor(Color.BLACK);//轴刻度颜色
		renderer.setShowLegend(true);        //图例
		renderer.setLegendTextSize(20);    //
		renderer.setXLabelsColor(Color.WHITE);
		renderer.setYLabelsColor(0, Color.BLACK);
		renderer.setGridColor(Color.LTGRAY);//网格颜色
		renderer.setApplyBackgroundColor(true);
		renderer.setBackgroundColor(Color.WHITE);//背景颜色
		renderer.setMargins(new int[]{70, 20, 30, 5});//边距 上 左 下 右
		XYSeriesRenderer r1 = new XYSeriesRenderer();
		r1.setColor(Color.BLUE);//数据点颜色
		r1.setChartValuesTextSize(15);
		r1.setChartValuesSpacing(1);
		r1.setPointStyle(PointStyle.POINT);//数据点形状
		r1.setFillBelowLine(false);
		r1.setFillPoints(true);
		XYSeriesRenderer r2 = new XYSeriesRenderer();
		r2.setColor(Color.RED);//数据点颜色
		r2.setChartValuesTextSize(15);
		r2.setChartValuesSpacing(1);
		r2.setPointStyle(PointStyle.POINT);//数据点形状
		r2.setFillBelowLine(false);
		r2.setFillPoints(true);
		XYSeriesRenderer r3 = new XYSeriesRenderer();
		r3.setColor(Color.GREEN);//数据点颜色
		r3.setChartValuesTextSize(15);
		r3.setChartValuesSpacing(1);
		r3.setPointStyle(PointStyle.POINT);//数据点形状
		r3.setFillBelowLine(false);
		r3.setFillPoints(true);
		renderer.addSeriesRenderer(r1);
		renderer.addSeriesRenderer(r2);
		renderer.addSeriesRenderer(r3);
		renderer.setMarginsColor(Color.WHITE);
		renderer.setPanEnabled(true, true);
		renderer.setShowGrid(true);
		renderer.setYAxisMax(3600);//Y最大范围
		renderer.setYAxisMin(0);//Y最小范围
		renderer.setInScroll(true);  //滚动
		return renderer;
	}

	/**
	 *
	 * @param dataset
	 * @param series1
	 * @param series2
	 * @return
	 */
	private XYMultipleSeriesDataset getDateDemoDataset(XYMultipleSeriesDataset dataset, TimeSeries series1, TimeSeries series2, TimeSeries series3) {
		final int nr = 201;                                        //200个数
		for (int k = 0; k < nr; k++) {
			series1.add(k, 0);    //初始化为0
			series2.add(k, 0);
			series3.add(k, 0);
		}
		dataset.addSeries(series1);                            //图表数据集加入数据组
		dataset.addSeries(series2);
		dataset.addSeries(series3);

		Log.i(TAG, dataset.toString());
		return dataset;                                    //返回数据集
	}

	/**
	 * 记录要连接的设备，获取蓝牙适配
	 */
	private void initBLEConfig() {
		for (int i = 0; i < connectDeviceMacList.size(); i++) {
			BluetoothGatt gatt = bluetoothAdapter.getRemoteDevice(connectDeviceMacList.get(i)).connectGatt(this, false, new BluetoothGattCallback() {
			});
			gattArrayList.add(gatt);
			Log.i("zbp", "添加" + connectDeviceMacList.get(i));
		}
		// 获取多蓝牙适配器
		multiConnectManager = BleManager.getMultiConnectManager(this);
		try {
			// 获取蓝牙适配器
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (bluetoothAdapter == null) {
				Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_LONG).show();
				return;
			}
			// 蓝牙没打开的时候打开蓝牙
			if (!bluetoothAdapter.isEnabled())
				bluetoothAdapter.enable();
		} catch (Exception err) {
			Log.i("zbp", "something wrong");
		}

		BleManager.setBleParamsOptions(new BleParamsOptions.Builder()
				.setBackgroundBetweenScanPeriod(1 * 60 * 1000)//在后台时（不可见扫描界面）扫描间隔暂停时间，我们扫描的方式是间隔扫描
				.setBackgroundScanPeriod(10000)//在后台时（不可见扫描界面）扫描持续时间
				.setForegroundBetweenScanPeriod(2000)//在前台时（可见扫描界面）扫描间隔暂停时间，我们扫描的方式是间隔扫描
				.setForegroundScanPeriod(10000)//在前台时（可见扫描界面）扫描持续时间
				.setDebugMode(BuildConfig.DEBUG)
				.setMaxConnectDeviceNum(4)            //最大可以连接的蓝牙设备个数
				.setReconnectBaseSpaceTime(1000)      //重连基础时间间隔 ms，重连的时间间隔
				.setReconnectMaxTimes(Integer.MAX_VALUE)//最大重连次数，默认可一直进行重连
				.setReconnectStrategy(ConnectConfig.RECONNECT_LINE_EXPONENT)
				.setReconnectedLineToExponentTimes(5)//快速重连的次数(线性到指数，只在 reconnectStrategy=ConnectConfig.RECONNECT_LINE_EXPONENT 时有效)
				.setConnectTimeOutTimes(5000)////连接超时时间 15s,15s 后自动检测蓝牙状态
				// （如果设备不在连接范围或蓝牙关闭，则重新连接的时间会很长，或者一直处于连接的状态，现在超时后会自动检测当前状态
				.build());
	}

	/**
	 * 连接蓝牙设备
	 */
	private void connentBluetooth() {
		String[] objects = connectDeviceMacList.toArray(new String[connectDeviceMacList.size()]);
		multiConnectManager.addDeviceToQueue(objects);
		multiConnectManager.addConnectStateListener(new ConnectStateListener() {
			@Override
			public void onConnectStateChanged(String address, ConnectState state) {
				switch (state) {
					case CONNECTING:
						Log.i("connectStateX", "设备:" + address + "连接状态:" + "正在连接");
						break;
					case CONNECTED:
						String[] devices = connectDeviceMacList.toArray(new String[connectDeviceMacList.size()]);
						for (int i = 0; i < connectDeviceMacList.size(); i++) {
							if (address.equals(devices[i]))
								CONNECT_STATA = true;
						}
						Log.i("connectStateX", "设备:" + address + "连接状态:" + "成功");
						break;
					case NORMAL:
						String[] devices2 = connectDeviceMacList.toArray(new String[connectDeviceMacList.size()]);
						for (int i = 0; i < connectDeviceMacList.size(); i++) {
							if (address.equals(devices2[i]))
								CONNECT_STATA = false;
						}
						Log.i("connectStateX", "设备:" + address + "连接状态:" + "失败");
						break;
				}
			}
		});

		/*数据回调*/
		multiConnectManager.setBluetoothGattCallback(new BluetoothGattCallback() {
			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) { //Characteristic 改变，数据接收
				super.onCharacteristicChanged(gatt, characteristic);
				//Logger.i("数据接受回调");
				dealCallDatas(gatt, characteristic);
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				//mBluetoothGatt = gatt;
				notifyCharacteristic = gatt.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")).getCharacteristic(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"));
				writeCharacteristic = gatt.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")).getCharacteristic(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"));
				bluetoothHashMap.put(gatt, writeCharacteristic);
			}

			@Override
			public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
				if(newState == BluetoothProfile.STATE_CONNECTED){
					gatt.discoverServices();
				}
			}

			@Override
			public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				super.onCharacteristicWrite(gatt, characteristic, status);
				Logger.i("数据发送");
			}
		});
		//1.服务UUID
		multiConnectManager.setServiceUUID("0000fff0-0000-1000-8000-00805f9b34fb");
		//2.clean history descriptor data（清除历史订阅读写通知）
		multiConnectManager.cleanSubscribeData();
		//3.add subscribe params（读写和通知）
		multiConnectManager.addBluetoothSubscribeData(
				new BluetoothSubScribeData.Builder().
						setDescriptorWrite(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), UUID.fromString(GattAttributeResolver.CLIENT_CHARACTERISTIC_CONFIG), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE).build());//特性UUID启用CCCD
		multiConnectManager.addBluetoothSubscribeData(
				new BluetoothSubScribeData.Builder().
						setDescriptorWrite(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), UUID.fromString(GattAttributeResolver.CLIENT_CHARACTERISTIC_CONFIG), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE).build());
		//还有读写descriptor
		multiConnectManager.addBluetoothSubscribeData(
				//new BluetoothSubScribeData.Builder().setCharacteristicNotify(UUID.fromString("0000e1d3-0000-1000-8000-00805f9b34fb")).build());//特性UUID
				new BluetoothSubScribeData.Builder().setCharacteristicNotify(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")).build());
		//start descriptor(注意，在使用时当回调onServicesDiscovered成功时会自动调用该方法，所以只需要在连接之前完成1,3步即可)
		for (int i = 0; i < gattArrayList.size(); i++) {
			multiConnectManager.startSubscribe(gattArrayList.get(i));
		}
		multiConnectManager.startConnect();

	}


	/**
	 * 处理回调的数据
	 *
	 * @param gatt
	 * @param characteristic
	 */
	private void dealCallDatas(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
		// 获取上传数据
		byte[] value = characteristic.getValue();

		Logger.i("数据长度：" + value.length);
		Logger.i("设备地址：" + gatt.getDevice().getAddress());
		switch(gatt.getDevice().getAddress()){
			case GASPRESSUREADDR:
				GP_DATA_BUFFER[0] = System.currentTimeMillis();
				GP_DATA_BUFFER[1] = Long.valueOf(((((short) value[0]) << 8) | ((short) value[1] & 0xff)));
				GP_DATA_BUFFER[1] = GP_DATA_BUFFER[1] * 3600L / 1024L;
				Logger.i("时间:" + GP_DATA_BUFFER[0] + "气箱数据:" +GP_DATA_BUFFER[1]);
				if (RECORD_STATA) {
					ArrayList<Long> list = new ArrayList<>(GP_DATA_BUFFER.length);
					Collections.addAll(list, GP_DATA_BUFFER);
					GP_Record_Data.add(list);
				}
				break;
			case BLOODPRESSUREADDR:
				// 缓存区，更新时从BP_DATA_BUFFER里取
				BP_DATA_BUFFER[0] = System.currentTimeMillis();
				BP_DATA_BUFFER[1] = Long.valueOf(((((short) value[0]) << 8) | ((short) value[1] & 0xff)));
				BP_DATA_BUFFER[1] = BP_DATA_BUFFER[1] * 3600L / 1024L;
				Logger.i("时间:" + BP_DATA_BUFFER[0] + "脉搏数据:" + BP_DATA_BUFFER[1]);
				if (RECORD_STATA) {
					ArrayList<Long> list = new ArrayList<>(BP_DATA_BUFFER.length);
					Collections.addAll(list, BP_DATA_BUFFER);
					BP_Record_Data.add(list);
				}
				break;
			case CUFFPRESSUREADDR:
				if (value.length == 12) {
					CP_DATA_BUFFER[0] = System.currentTimeMillis();
					CP_DATA_BUFFER[1] = Long.valueOf(((((short) value[7]) << 8) | ((short) value[8] & 0xff)));
					Logger.i("时间:" + CP_DATA_BUFFER[0] + "袖带数据:" + CP_DATA_BUFFER[1]);
					if (RECORD_STATA) {
						ArrayList<Long> list = new ArrayList<>(CP_DATA_BUFFER.length);
						Collections.addAll(list, CP_DATA_BUFFER);
						CP_Record_Data.add(list);
					}
				}
			default:
				break;
		}
		EventBus.getDefault().post(new RefreshDatas()); // 发送消息，更新UI 显示数据 ④发送事件
	}

	private void startInflating(){
		for(Map.Entry<BluetoothGatt, BluetoothGattCharacteristic> entry:bluetoothHashMap.entrySet()){
			if (entry.getKey().getDevice().getAddress().equals(CUFFPRESSUREADDR)){
				entry.getValue().setValue(BLOODSTART);
				entry.getKey().writeCharacteristic(entry.getValue());
			}
		}
	}

	private void stopBloodAndSPO2(){
		for(Map.Entry<BluetoothGatt, BluetoothGattCharacteristic> entry:bluetoothHashMap.entrySet()){
			if (entry.getKey().getDevice().getAddress().equals(CUFFPRESSUREADDR)){
				entry.getValue().setValue(BLOODSTOP);
				entry.getKey().writeCharacteristic(entry.getValue());
			}
		}
	}

	public void onEventMainThread(RefreshDatas event) { //   ③处理事件

	}

	/**
	 * createAt 2019/8/30
	 * description:  权限申请相关，适配6.0+机型 ，蓝牙，文件，位置 权限
	 */
	private String[] allPermissionList = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
			Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
	};

	/**
	 * 遍历出需要获取的权限
	 */
	private void requestWritePermission() {
		ArrayList<String> permissionList = new ArrayList<>();
		// 将需要获取的权限加入到集合中  ，根据集合数量判断 需不需要添加
		for (int i = 0; i < allPermissionList.length; i++) {
			if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(this, allPermissionList[i])) {
				permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			}
		}
		String permissionArray[] = new String[permissionList.size()];
		for (int i = 0; i < permissionList.size(); i++) {
			permissionArray[i] = permissionList.get(i);
		}
		if (permissionList.size() > 0)
			ActivityCompat.requestPermissions(this, permissionArray, REQUEST_CODE_PERMISSION);
	}

	/**
	 * 权限申请的回调
	 *
	 * @param requestCode
	 * @param permissions
	 * @param grantResults
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_CODE_PERMISSION) {
			if (permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				//用户同意使用write
			} else {
				//用户不同意，自行处理即可
				Toast.makeText(ChartsActivity.this, "您取消了权限申请,可能会影响软件的使用,如有问题请退出重试", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onDestroy() {
		//Timer
		timer.cancel();
		EventBus.getDefault().unregister(this);//   ⑤		取消事件
		super.onDestroy();
	}
}


	
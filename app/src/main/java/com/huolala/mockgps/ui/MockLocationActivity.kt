package com.huolala.mockgps.ui

import android.content.Intent
import android.os.*
import android.view.View
import android.widget.Toast
import com.huolala.mockgps.server.GpsAndFloatingService

import com.baidu.location.BDLocation

import com.baidu.location.BDAbstractLocationListener

import com.baidu.location.LocationClientOption

import com.baidu.location.LocationClient
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.baidu.mapapi.search.route.*
import com.huolala.mockgps.model.MockMessageModel
import com.huolala.mockgps.utils.Utils
import kotlinx.android.synthetic.main.activity_navi.*

import com.baidu.mapapi.map.OverlayOptions
import com.blankj.utilcode.util.ClickUtils

import com.blankj.utilcode.util.FileIOUtils
import com.castiel.common.base.BaseActivity
import com.castiel.common.base.BaseViewModel
import com.huolala.mockgps.R
import com.huolala.mockgps.databinding.ActivityNaviBinding
import com.huolala.mockgps.model.NaviType
import java.lang.ref.WeakReference


/**
 * @author jiayu.liu
 */
class MockLocationActivity : BaseActivity<ActivityNaviBinding, BaseViewModel>(),
    View.OnClickListener {
    private var DRAW_MAP = 0;
    private lateinit var mLocationClient: LocationClient
    private lateinit var mBaiduMap: BaiduMap
    private var mSearch: RoutePlanSearch = RoutePlanSearch.newInstance()
    private var mNaviType: Int = NaviType.LOCATION
    private val mHandle: Handler = MockLocationHandler(this)

    //注册LocationListener监听器
    private val myLocationListener = object : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation?) {
            //mapView 销毁后不在处理新接收的位置
            if (location == null) {
                return
            }
            mBaiduMap.locationData?.run {
                //如果相等 不能更新
                if (latitude == location.latitude && longitude == location.longitude) {
                    return
                }
            }
            val locData = MyLocationData.Builder()
                .accuracy(location.radius) // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(location.direction).latitude(location.latitude)
                .longitude(location.longitude).build()
            mBaiduMap.setMyLocationData(locData)
            //更新中心点
            if (mNaviType == NaviType.LOCATION) {
                mBaiduMap.animateMapStatus(
                    MapStatusUpdateFactory.newLatLngZoom(
                        LatLng(
                            locData.latitude,
                            locData.longitude
                        ), 16f
                    )
                )
            }

        }
    }


    override fun initViewModel(): Class<BaseViewModel> {
        return BaseViewModel::class.java
    }

    override fun getLayout(): Int {
        return R.layout.activity_navi
    }

    override fun initView() {
        ClickUtils.applySingleDebouncing(iv_back, this)

        mBaiduMap = mapview.map
        mBaiduMap.isMyLocationEnabled = true

        mBaiduMap.setMyLocationConfiguration(
            MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL,
                true, null
            )
        )

        mBaiduMap.setMapStatus(MapStatusUpdateFactory.zoomBy(16f))

        mLocationClient = LocationClient(this)

        //通过LocationClientOption设置LocationClient相关参数
        val option = LocationClientOption()
        option.isOpenGps = true // 打开gps
        option.setScanSpan(1000)

        //设置locationClientOption
        mLocationClient.locOption = option

        mLocationClient.registerLocationListener(myLocationListener)
        //开启地图定位图层
        mLocationClient.start()

        mSearch.setOnGetRoutePlanResultListener(object : OnGetRoutePlanResultListener {
            override fun onGetWalkingRouteResult(p0: WalkingRouteResult?) {
            }

            override fun onGetTransitRouteResult(p0: TransitRouteResult?) {
            }

            override fun onGetMassTransitRouteResult(p0: MassTransitRouteResult?) {
            }

            override fun onGetDrivingRouteResult(drivingRouteResult: DrivingRouteResult?) {
                //创建DrivingRouteOverlay实例
                drivingRouteResult?.routeLines?.get(0)?.run {
                    val polylineList = arrayListOf<LatLng>()
                    for (step in allStep) {
                        if (step.wayPoints != null && step.wayPoints.isNotEmpty()) {
                            polylineList.addAll(step.wayPoints)
                        }
                    }
                    drawLineToMap(polylineList)
                }
            }

            override fun onGetIndoorRouteResult(p0: IndoorRouteResult?) {
            }

            override fun onGetBikingRouteResult(p0: BikingRouteResult?) {
            }
        })
    }

    override fun initData() {
        val model = intent.getParcelableExtra<MockMessageModel>("model")
        if (model == null) {
            pickPoiError()
            return
        }
        model.run {
            this@MockLocationActivity.mNaviType = naviType
            when (naviType) {
                NaviType.LOCATION -> {
                    locationModel?.run {
                        startMockServer(model)
                    } ?: {
                        pickPoiError()
                    }
                }
                NaviType.NAVI -> {
                    if (startNavi == null || endNavi == null) {
                        pickPoiError()
                        return
                    }
                    mSearch.drivingSearch(
                        DrivingRoutePlanOption()
                            .policy(DrivingRoutePlanOption.DrivingPolicy.ECAR_DIS_FIRST)
                            .from(PlanNode.withLocation(startNavi?.latLng))
                            .to(PlanNode.withLocation(endNavi?.latLng))
                    )
                    startMockServer(model)
                }
                NaviType.NAVI_FILE -> {
                    try {
                        val polylineList = arrayListOf<LatLng>()
                        val readFile2String = FileIOUtils.readFile2String(path)
                        readFile2String?.run {
                            split(";").run {
                                if (isNotEmpty()) {
                                    map {
                                        it.split(",").run {
                                            if (size == 2) {
                                                polylineList.add(
                                                    LatLng(
                                                        get(1).toDouble(),
                                                        get(0).toDouble()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        mHandle.sendMessageDelayed(Message.obtain().apply {
                            what = DRAW_MAP
                            obj = polylineList
                        }, 500)
                    } catch (e: Exception) {
                    }
                    startMockServer(model)
                }
                else -> {
                }
            }
        }
    }

    override fun initObserver() {

    }

    private fun pickPoiError() {
        Toast.makeText(this, "选址数据异常，请重新选择地址再重试", Toast.LENGTH_SHORT).show()
        finish()
    }


    private fun drawLineToMap(polylineList: ArrayList<LatLng>?) {
        mBaiduMap.clear()
        if (polylineList == null || polylineList.size == 0) {
            return
        }

        val mOverlayOptions: OverlayOptions = PolylineOptions()
            .width(15)
            .color(0xAAFF0000.toInt())
            .keepScale(true)
            .lineCapType(PolylineOptions.LineCapType.LineCapRound)
            .points(polylineList)

        mBaiduMap.addOverlay(mOverlayOptions)
        mBaiduMap.animateMapStatus(
            MapStatusUpdateFactory.newLatLngBounds(
                LatLngBounds.Builder().include(polylineList).build(), 50, 50, 50, 50
            )
        )
    }

    private fun startMockServer(parcelable: Parcelable?) {
        //判断  为null先启动服务  悬浮窗需要
        parcelable?.run {
            if (!Utils.isAllowMockLocation(this@MockLocationActivity)) {
                Toast.makeText(
                    this@MockLocationActivity,
                    "将本应用设置为\"模拟位置信息应用\"，否则无法正常使用",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        //启动服务  定位以及悬浮窗
        startService(Intent(this, GpsAndFloatingService::class.java).apply {
            parcelable?.let {
                putExtras(
                    Bundle().apply {
                        putParcelable("info", it)
                    })
            }
        })
    }

    override fun onResume() {
        super.onResume()
        mapview.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapview.onPause()
        if (isFinishing) {
            destroy()
        }
    }

    private fun destroy() {
        mSearch.destroy()
        mLocationClient.unRegisterLocationListener(myLocationListener)
        mLocationClient.stop()
        mBaiduMap.isMyLocationEnabled = false
        mapview.onDestroy()
    }

    override fun onClick(v: View?) {
        when (v) {
            iv_back -> {
                finish()
            }
            else -> {
            }
        }
    }

    class MockLocationHandler(activity: MockLocationActivity) :
        Handler(Looper.getMainLooper()) {
        private var weakReference: WeakReference<MockLocationActivity>? = null

        init {
            weakReference = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            weakReference?.get()?.run {
                when (msg.what) {
                    DRAW_MAP -> {
                        (msg.obj as ArrayList<LatLng>?)?.let {
                            drawLineToMap(it)
                        }

                    }
                    else -> {
                    }
                }

            }
        }

    }

}
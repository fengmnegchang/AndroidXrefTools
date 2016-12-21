package com.devdiv.test.bitmap_test;

/*
 * 1 true和false的设置问题，matrix应用时
 */

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class Bitmap_testActivity extends Activity {
	
	private static final int DELAY_TIME = 3000;
	 ImageView mImageView1;
     ImageView mImageView2;
     ImageView mImageView3;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
          mImageView1 = (ImageView) findViewById(R.id.imageview1);
          mImageView2 = (ImageView) findViewById(R.id.imageview2);
          mImageView3 = (ImageView) findViewById(R.id.imageview3);
//        mImageView1.setImageBitmap(getDrawableBitmap());
//        mImageView1.setImageBitmap(getResourceBitmap());
//        mImageView1.setImageBitmap(getAssetsBitmap());
//        mImageView1.setImageBitmap(drawGraphics());
        
//        Bitmap mSaveBitmap = drawGraphics();
//        mImageView1.setImageBitmap(mSaveBitmap);
//        注意添加相应的权限，在manifest.xml中
//        saveBitmap(mSaveBitmap, "/sdcard/savebitmap123.PNG");
        
//        mImageView1.setImageResource(R.drawable.android);
//        mImageView2.setImageResource(R.drawable.pet);
//        getDrawingCache(mImageView1, mImageView2);
        
//        mImageView1.setImageBitmap(getRoundedBitmap());
//        mImageView2.setImageResource(R.drawable.frame);
        
//        mImageView1.setImageResource(R.drawable.android);
//        mImageView2.setImageBitmap(getGrayBitmap());
        
//        mImageView1.setImageResource(R.drawable.enemy_infantry_ninja);
//        mImageView2.setImageBitmap(getAlphaBitmap());
//        mImageView3.setImageBitmap(getStrokeBitmap());
        
//        mImageView1.setImageResource(R.drawable.pet);
//        mImageView2.setImageBitmap(getScaleBitmap());
//        mImageView2.setImageBitmap(getRotatedBitmap());
//        mImageView2.setImageBitmap(getScrewBitmap());
//        mImageView2.setImageBitmap(getInverseBitmap());
//        mImageView2.setImageBitmap(getReflectedBitmap());
        
        
//        mImageView1.setImageBitmap(getCompoundedBitmap());
        
//        mImageView1.setImageBitmap(getClipBitmap());
//        mImageView1.setImageResource(R.drawable.sprite_icon_privilege);
//        mImageView2.setImageBitmap(getMultiBitmap());
          
          showUninstallAPKIcon("/sdcard/yunshitingjspjb.apk");  
          
          //4.1 6.0 
//          getUninatllApkInfo(this, "/sdcard/yunshitingjspjb.apk");  
          
          mImageView3.setOnClickListener(new View.OnClickListener() {
  			@Override
  			public void onClick(View v) {
  				new Thread() {
  					/*
  					 * (non-Javadoc)
  					 * 
  					 * @see java.lang.Thread#run()
  					 */
  					@Override
  					public void run() {
  						try {
  							// Return an AssetManager instance for your
  							// application's package
  							InputStream instream = getAssets().open("PackageParser.java");
  							if (instream != null) {
  								InputStreamReader inputreader = new InputStreamReader(instream);
  								BufferedReader buffreader = new BufferedReader(inputreader);
  								String line;
  								StringBuilder buffer = new StringBuilder();
  								// 分行读取
  								while ((line = buffreader.readLine()) != null) {
  									String conent = line;
  									if (conent != null && conent.length() > 0) {
  										Pattern p = Pattern.compile("^[1-9]\\d*");
  										Matcher m = p.matcher(conent);
  										while (m.find()) {
  											String s1 = m.group();
  											System.out.println(s1+"===="+conent);
  											buffer.append(conent.replace(s1, "")).append("\n");
  										}
  									}
  								}
  								instream.close();

  								if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
  									File sdDir = Environment.getExternalStorageDirectory();
  									if (!sdDir.exists()) {
  										sdDir.mkdirs();
  									}
  									System.out.println(sdDir);
  									FileOutputStream fout;
  									try {
  										File f = new File(sdDir.getCanonicalPath() + "/v1.java");
  										f.deleteOnExit();
  										f.createNewFile();
  										fout = new FileOutputStream(f);
  										BufferedOutputStream bout = new BufferedOutputStream(fout);
  										bout.write(buffer.toString().getBytes());
  										bout.flush();
  										bout.close();
  									} catch (FileNotFoundException e) {
  										e.printStackTrace();
  									} catch (IOException e) {
  										e.printStackTrace();
  									}
  								}
  							}
  						} catch (IOException e) {
  							// Should never happen!
  							throw new RuntimeException(e);
  						}

  					}
  				}.start();
  			}
  		});
    }
    
    /** 网传的方法,但获取不成功 */  
    public void getUninatllApkInfo(Context context, String archiveFilePath) {  
        PackageManager pm = context.getPackageManager();  
        PackageInfo info = pm.getPackageArchiveInfo(archiveFilePath, PackageManager.GET_ACTIVITIES);  
        if (info != null) {  
            ApplicationInfo appInfo = info.applicationInfo;  
            Drawable icon = pm.getApplicationIcon(appInfo);  
            mImageView1.setImageDrawable(icon);  
        }  
    }  
     
    private void showUninstallAPKIcon(String apkPath) {  
        String PATH_PackageParser = "android.content.pm.PackageParser";  
        String PATH_AssetManager = "android.content.res.AssetManager";  
        try {  
            // apk包的文件路径  
            // 这是一个Package 解释器, 是隐藏的  
            // 构造函数的参数只有一个, apk文件的路径  
            // PackageParser packageParser = new PackageParser(apkPath); 
        	
        	//5.1 PackageParser packageParser = new PackageParser();
            Class pkgParserCls = Class.forName(PATH_PackageParser);  
            Class[] typeArgs = new Class[0];  
//            typeArgs[0] = String.class;  
            Constructor pkgParserCt = pkgParserCls.getConstructor(typeArgs);  
            Object[] valueArgs = new Object[0];  
//            valueArgs[0] = apkPath;  
            Object pkgParser = pkgParserCt.newInstance(valueArgs);  
            Log.d("ANDROID_LAB", "pkgParser:" + pkgParser.toString());  
            // 这个是与显示有关的, 里面涉及到一些像素显示等等, 我们使用默认的情况  
            DisplayMetrics metrics = new DisplayMetrics();  
            metrics.setToDefaults();  
            // PackageParser.Package mPkgInfo = packageParser.parsePackage(new  
            // File(apkPath), apkPath,  
            // metrics, 0);  
            
            /**
             * 5.1
             * /    public Package parsePackage(File packageFile, int flags) throws PackageParserException {
//        if (packageFile.isDirectory()) {
//            return parseClusterPackage(packageFile, flags);
//        } else {
//            return parseMonolithicPackage(packageFile, flags);
//        }
//    }
             */
//            typeArgs = new Class[4];  
//            typeArgs[0] = File.class;  
//            typeArgs[1] = String.class;  
//            typeArgs[2] = DisplayMetrics.class;  
//            typeArgs[3] = Integer.TYPE;  
            
            typeArgs = new Class[2];  
            typeArgs[0] = File.class;  
//            typeArgs[1] = String.class;  
//            typeArgs[2] = DisplayMetrics.class;  
            typeArgs[1] = Integer.TYPE;  
            Method pkgParser_parsePackageMtd = pkgParserCls.getDeclaredMethod("parsePackage",  
                    typeArgs);  
            
//            valueArgs = new Object[4];  
//            valueArgs[0] = new File(apkPath);  
//            valueArgs[1] = apkPath;  
//            valueArgs[2] = metrics;  
//            valueArgs[3] = 0;  
            valueArgs = new Object[2];  
            valueArgs[0] = new File(apkPath);  
//            valueArgs[1] = apkPath;  
//            valueArgs[2] = metrics;  
            valueArgs[1] = 0;  
            Object pkgParserPkg = pkgParser_parsePackageMtd.invoke(pkgParser, valueArgs);  
            // 应用程序信息包, 这个公开的, 不过有些函数, 变量没公开  
            // ApplicationInfo info = mPkgInfo.applicationInfo;  
            Field appInfoFld = pkgParserPkg.getClass().getDeclaredField("applicationInfo");  
            ApplicationInfo info = (ApplicationInfo) appInfoFld.get(pkgParserPkg);  
            // uid 输出为"-1"，原因是未安装，系统未分配其Uid。  
            Log.d("ANDROID_LAB", "pkg:" + info.packageName + " uid=" + info.uid);  
            // Resources pRes = getResources();  
            // AssetManager assmgr = new AssetManager();  
            // assmgr.addAssetPath(apkPath);  
            // Resources res = new Resources(assmgr, pRes.getDisplayMetrics(),  
            // pRes.getConfiguration());  
            Class assetMagCls = Class.forName(PATH_AssetManager);  
            Constructor assetMagCt = assetMagCls.getConstructor((Class[]) null);  
            Object assetMag = assetMagCt.newInstance((Object[]) null);  
            typeArgs = new Class[1];  
            typeArgs[0] = String.class;  
            Method assetMag_addAssetPathMtd = assetMagCls.getDeclaredMethod("addAssetPath",  
                    typeArgs);  
            valueArgs = new Object[1];  
            valueArgs[0] = apkPath;  
            assetMag_addAssetPathMtd.invoke(assetMag, valueArgs);  
            Resources res = getResources();  
            typeArgs = new Class[3];  
            typeArgs[0] = assetMag.getClass();  
            typeArgs[1] = res.getDisplayMetrics().getClass();  
            typeArgs[2] = res.getConfiguration().getClass();  
            Constructor resCt = Resources.class.getConstructor(typeArgs);  
            valueArgs = new Object[3];  
            valueArgs[0] = assetMag;  
            valueArgs[1] = res.getDisplayMetrics();  
            valueArgs[2] = res.getConfiguration();  
            res = (Resources) resCt.newInstance(valueArgs);  
            CharSequence label = null;  
            if (info.labelRes != 0) {  
                label = res.getText(info.labelRes);  
            }  
            // if (label == null) {  
            // label = (info.nonLocalizedLabel != null) ? info.nonLocalizedLabel  
            // : info.packageName;  
            // }  
            Log.d("ANDROID_LAB", "label=" + label);  
            // 这里就是读取一个apk程序的图标  
            if (info.icon != 0) {  
                Drawable icon = res.getDrawable(info.icon);  
                mImageView2.setImageDrawable(icon);  
            }  
        } catch (Exception e) {  
            e.printStackTrace();  
        }  
    } 
    
   // 裁剪
  	public Bitmap getMultiBitmap() {
  		BitmapDrawable bd = (BitmapDrawable) getResources().getDrawable(
  				R.drawable.sprite_icon_privilege);
  		Bitmap bitmap = bd.getBitmap();
  		int w = bitmap.getWidth();
  		int h = bitmap.getHeight();
  		
//  		Bitmap bm = Bitmap.createBitmap(150, 150, Config.ARGB_8888);
//  		Canvas canvas = new Canvas(bm);
//  		Paint mPaint = new Paint();
//  		mPaint.setAntiAlias(true);
//  		mPaint.setStyle(Style.STROKE);
//  		
//  		canvas.drawBitmap(bitmap, 0, 0, mPaint);
  		
  		Bitmap bm = Bitmap.createBitmap(bitmap, 150, 0, 150, 150);
  		Canvas canvas = new Canvas(bm);
  		Paint mPaint = new Paint();
  		mPaint.setAntiAlias(true);
  		mPaint.setStyle(Style.STROKE);
  		
  		canvas.drawBitmap(bitmap, 150, 0, mPaint);
//  		
//  		int deltX = 150;
//  		int deltY = 1;
//  		
//  		DashPathEffect dashStyle = new DashPathEffect(new float[] { 10, 5,
//  				5, 5 }, 2);
//  		
//  		RectF faceRect = new RectF(0, 0, 170, 150);
////  		float [] faceCornerii = new float[] {
////  				30,30,30,30,75,75,75,75
////  		};
//  		mPaint.setColor(0xFF6F8DD5);
//  		mPaint.setStrokeWidth(6);
//  		mPaint.setPathEffect(dashStyle);
//  		Path clip = new Path();
//  		clip.reset();
//  		clip.addRect(faceRect, Direction.CW);
//  		//注意addRoundRect的构造方法的各个参数
////  		clip.addRoundRect(faceRect, faceCornerii, Direction.CW);
//  		
//  		canvas.save();
//  		canvas.translate(deltX, deltY);
//  		//注意Region.Op中各种叠加方式的使用
//  		canvas.clipPath(clip, Region.Op.DIFFERENCE);
//  		canvas.drawColor(0xDF222222);
//  		canvas.drawPath(clip, mPaint);
//  		canvas.restore();
//  		
//  		
//  		Rect srcRect = new Rect(0, 0, 170, 150);
//  		srcRect.offset(deltX, deltY);
//  		//为canvas添加DrawFilter
//  		PaintFlagsDrawFilter dfd = new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG);
//  		canvas.setDrawFilter(dfd);
//  		canvas.clipPath(clip);
//  		canvas.drawBitmap(bitmap, srcRect, faceRect, mPaint);
  		
  		 
  		return bm;
  	}
    
    //获取res目录下的drawable资源
    public Bitmap getDrawableBitmap() {
    	//获取应用资源集管理实例
    	Resources mResources = getResources();
    	//获取drawable资源frame，转换为 BitmapDrawable类型
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) mResources.getDrawable(R.drawable.android);
    	//获取bitmap
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	
    	return mBitmap;
    }
        
    //获取res目录下的drawable资源
    public Bitmap getResourceBitmap() {
    	Resources mResources = getResources();
    	Bitmap mBitmap = BitmapFactory.decodeResource(mResources, R.drawable.android);
    	
    	return mBitmap;
    }
      
    //获取assets目录下的图片资源
    public Bitmap getAssetsBitmap() {
    	//定义Bitmap
    	Bitmap mBitmap = null;
    	
    	//获取assets资源管理实例
    	AssetManager mAssetManager = getAssets();
    	
    	try {
    		//打开frame.png文件流
			InputStream mInputStream = mAssetManager.open("android.png");
			//通过decodeStream方法解析文件流
			mBitmap = BitmapFactory.decodeStream(mInputStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	return mBitmap;
    }
    
    //创建Bitmap资源
    public Bitmap drawGraphics() {
    	//创建大小为320 x 480的ARGB_8888类型位图
    	Bitmap mBitmap = Bitmap.createBitmap(320, 480, Config.ARGB_8888);
    	//把新建的位图作为画板
    	Canvas mCanvas = new Canvas(mBitmap);
    	
    	//先画一个黑屏
    	mCanvas.drawColor(Color.BLACK);

    	//创建画笔,并进行设置
    	Paint mPaint = new Paint();
    	mPaint.setColor(Color.BLUE);
    	mPaint.setStyle(Style.FILL);
    	
    	Rect mRect = new Rect(10, 10, 300, 80);
    	RectF mRectF = new RectF(mRect);
    	//设置圆角半径
    	float roundPx = 15;
    	
    	mPaint.setAntiAlias(true);
    	mCanvas.drawRoundRect(mRectF, roundPx, roundPx, mPaint);
    	mPaint.setColor(Color.GREEN);
    	mCanvas.drawCircle(80, 180, 80, mPaint);
    	
    	DashPathEffect mDashPathEffect = new DashPathEffect(new float[] {20, 20, 10, 10, 5, 5,}, 0);
    	mPaint.setPathEffect(mDashPathEffect);
    	Path mPath = new Path();
    	mRectF.offsetTo(10, 300);
    	mPath.addRect(mRectF, Direction.CW);
    	
    	mPaint.setColor(Color.RED);
    	mPaint.setStrokeWidth(5);
    	mPaint.setStrokeJoin(Join.ROUND);
    	mPaint.setStyle(Style.STROKE);
    	mCanvas.drawPath(mPath, mPaint);
    	
    	mCanvas.drawBitmap(getDrawableBitmap(), 160, 90, mPaint);
    	
    	return mBitmap;   	
    }

    //保存位图资源
    //不知道如何使用
    public static void saveBitmap(Bitmap bitmap, String path) {
    	FileOutputStream mFileOutputStream = null;
    	
    	try {
    		File mFile = new File(path);
    		//创建文件
			mFile.createNewFile();
			//创建文件输出流
			mFileOutputStream = new FileOutputStream(mFile);
			//保存Bitmap到PNG文件
			//图片压缩质量为75，对于PNG来说这个参数会被忽略
			bitmap.compress(CompressFormat.PNG, 75, mFileOutputStream);
			//Flushes this stream. 
			//Implementations of this method should ensure that any buffered data is written out. 
			//This implementation does nothing.
			mFileOutputStream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				mFileOutputStream.close();
			} catch (IOException e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
    	
    }
    
    //View转换为Bitmap
    public void getDrawingCache(final ImageView sourceImageView, final ImageView destImageView) {
    	
    	new Handler().postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				//开启bitmap缓存
				sourceImageView.setDrawingCacheEnabled(true);
				//获取bitmap缓存
				Bitmap mBitmap = sourceImageView.getDrawingCache();
				//显示 bitmap
				destImageView.setImageBitmap(mBitmap);
				
//				Bitmap mBitmap = sourceImageView.getDrawingCache();
//				Drawable drawable = (Drawable) new BitmapDrawable(mBitmap);
//				destImageView.setImageDrawable(drawable);
				
				new Handler().postDelayed(new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub
						//不再显示bitmap缓存
						//destImageView.setImageBitmap(null);
						destImageView.setImageResource(R.drawable.pet);
						
						//使用这句话而不是用上一句话是错误的，空指针调用
						//destImageView.setBackgroundDrawable(null);
						
						//关闭bitmap缓存
						sourceImageView.setDrawingCacheEnabled(false);
						//释放bitmap缓存资源
						sourceImageView.destroyDrawingCache();
					}
				}, DELAY_TIME);
			}
		}, DELAY_TIME);
    }
    
    //图片圆角处理
    public Bitmap getRoundedBitmap() {
    	Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.frame);
    	//创建新的位图
    	Bitmap bgBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Config.ARGB_8888);
    	//把创建的位图作为画板
    	Canvas mCanvas = new Canvas(bgBitmap);
    	
    	Paint mPaint = new Paint();
    	Rect mRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
    	RectF mRectF = new RectF(mRect);
    	//设置圆角半径为20
    	float roundPx = 15;
    	mPaint.setAntiAlias(true);
    	//先绘制圆角矩形
    	mCanvas.drawRoundRect(mRectF, roundPx, roundPx, mPaint);
    	
    	//设置图像的叠加模式
    	mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    	//绘制图像
    	mCanvas.drawBitmap(mBitmap, mRect, mRect, mPaint);
    	
    	return bgBitmap;
    }
    
    
    //图片灰化处理
    public Bitmap getGrayBitmap() {
    	Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.android);
    	Bitmap mGrayBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Config.ARGB_8888);
    	Canvas mCanvas = new Canvas(mGrayBitmap);
    	Paint mPaint = new Paint();
    	
    	//创建颜色变换矩阵
    	ColorMatrix mColorMatrix = new ColorMatrix();
    	//设置灰度影响范围
    	mColorMatrix.setSaturation(0);
    	//创建颜色过滤矩阵
    	ColorMatrixColorFilter mColorFilter = new ColorMatrixColorFilter(mColorMatrix);
    	//设置画笔的颜色过滤矩阵
    	mPaint.setColorFilter(mColorFilter);
    	//使用处理后的画笔绘制图像
    	mCanvas.drawBitmap(mBitmap, 0, 0, mPaint);
    	
    	return mGrayBitmap; 	
    }
    
    //提取图像Alpha位图
    public Bitmap getAlphaBitmap() {
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.enemy_infantry_ninja);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	
    	//BitmapDrawable的getIntrinsicWidth（）方法，Bitmap的getWidth（）方法
    	//注意这两个方法的区别
    	//Bitmap mAlphaBitmap = Bitmap.createBitmap(mBitmapDrawable.getIntrinsicWidth(), mBitmapDrawable.getIntrinsicHeight(), Config.ARGB_8888);
    	Bitmap mAlphaBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Config.ARGB_8888);
    	
    	Canvas mCanvas = new Canvas(mAlphaBitmap);
    	Paint mPaint = new Paint();
    	
    	mPaint.setColor(Color.BLUE);
    	//从原位图中提取只包含alpha的位图
    	Bitmap alphaBitmap = mBitmap.extractAlpha();
    	//在画布上（mAlphaBitmap）绘制alpha位图
    	mCanvas.drawBitmap(alphaBitmap, 0, 0, mPaint);
    	
    	return mAlphaBitmap;
    }
    
    //getStrokeBitmap
    public Bitmap getStrokeBitmap() {
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.enemy_infantry_ninja);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	int width = mBitmap.getWidth();
    	int height = mBitmap.getHeight();
    	Bitmap mAlphaBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
    	Canvas mCanvas = new Canvas(mAlphaBitmap);
    	Paint mPaint = new Paint();
    	
    	mPaint.setColor(Color.BLUE);
    	Bitmap alphaBitmap = mBitmap.extractAlpha();
    	mCanvas.drawBitmap(alphaBitmap, 0, 0, mPaint);
    	
    	//创建图像的矩形区域
    	Rect srcRect = new Rect(0, 0, width, height);
    	//创建图像的内矩形区域
    	Rect innerRect = new Rect(srcRect);
    	//向内缩进两个像素
    	innerRect.inset(2, 2);
    	//绘制原始图像
    	mCanvas.drawBitmap(mBitmap, srcRect, innerRect, mPaint);
    	
    	return mAlphaBitmap;
    }
    
    //getScaleBitmap
    public Bitmap getScaleBitmap() {
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.pet);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	int width = mBitmap.getWidth();
    	int height = mBitmap.getHeight();
    	
    	Matrix matrix = new Matrix();
    	matrix.preScale(0.75f, 0.75f);
    	Bitmap mScaleBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height, matrix, true);
    	
    	return mScaleBitmap;
    }
    
    //getRotatedBitmap
    public Bitmap getRotatedBitmap() {
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.pet);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	int width = mBitmap.getWidth();
    	int height = mBitmap.getHeight();
    	
    	Matrix matrix = new Matrix();
    	matrix.preRotate(45);
    	Bitmap mRotateBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height, matrix, true);
    	
    	return mRotateBitmap;
    }
    
    //getScrewBitmap
    public Bitmap getScrewBitmap() {
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.pet);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	int width = mBitmap.getWidth();
    	int height = mBitmap.getHeight();
    	
    	Matrix matrix = new Matrix();
    	matrix.preSkew(1.0f, 0.15f);
    	Bitmap mScrewBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height, matrix, true);
    	
    	return mScrewBitmap;
    }
    
    //getInverseBitmap
	public Bitmap getInverseBitmap() {
		BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.pet);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	int width = mBitmap.getWidth();
    	int height = mBitmap.getHeight();

		Matrix matrix = new Matrix();
		// 图片缩放，x轴变为原来的1倍，y轴为-1倍,实现图片的反转
		matrix.preScale(1, -1);
		Bitmap mInverseBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height, matrix, false);

		return mInverseBitmap;
	}
	
	
	private Bitmap getReflectedBitmap() {
		BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.pet);
		Bitmap mBitmap = mBitmapDrawable.getBitmap();
		int width = mBitmap.getWidth();
		int height = mBitmap.getHeight();
		
		Matrix matrix = new Matrix();
		// 图片缩放，x轴变为原来的1倍，y轴为-1倍,实现图片的反转
		matrix.preScale(1, -1);
		
		//创建反转后的图片Bitmap对象，图片高是原图的一半。
		//Bitmap mInverseBitmap = Bitmap.createBitmap(mBitmap, 0, height/2, width, height/2, matrix, false);
		//创建标准的Bitmap对象，宽和原图一致，高是原图的1.5倍。
		//注意两种createBitmap的不同
		//Bitmap mReflectedBitmap = Bitmap.createBitmap(width, height*3/2, Config.ARGB_8888);
		
		Bitmap mInverseBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height, matrix, false);
		Bitmap mReflectedBitmap = Bitmap.createBitmap(width, height*2, Config.ARGB_8888);
		
		// 把新建的位图作为画板
		Canvas mCanvas = new Canvas(mReflectedBitmap);
		//绘制图片
		mCanvas.drawBitmap(mBitmap, 0, 0, null);
		mCanvas.drawBitmap(mInverseBitmap, 0, height, null);
		
		//添加倒影的渐变效果
		Paint mPaint = new Paint();
		Shader mShader = new LinearGradient(0, height, 0, mReflectedBitmap.getHeight(), 0x70ffffff, 0x00ffffff, TileMode.MIRROR);
		mPaint.setShader(mShader);
		//设置叠加模式
		mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
		//绘制遮罩效果
		mCanvas.drawRect(0, height, width, mReflectedBitmap.getHeight(), mPaint);
		
		return mReflectedBitmap;
	}
		
	//getCompoundedBitmap
	public Bitmap getCompoundedBitmap() {
		
		BitmapDrawable bdHair = (BitmapDrawable) getResources().getDrawable(
				R.drawable.res_hair);
		Bitmap bitmapHair = bdHair.getBitmap();
		BitmapDrawable bdFace = (BitmapDrawable) getResources().getDrawable(
				R.drawable.res_face);
		Bitmap bitmapFace = bdFace.getBitmap();
		BitmapDrawable bdClothes = (BitmapDrawable) getResources().getDrawable(
				R.drawable.res_clothes);
		Bitmap bitmapClothes = bdClothes.getBitmap();
		
		int w = bdClothes.getIntrinsicWidth();
		int h = bdClothes.getIntrinsicHeight();
		Bitmap mBitmap = Bitmap.createBitmap(w, h, Config.ARGB_8888);
		
		Canvas canvas = new Canvas(mBitmap);
		Paint mPaint = new Paint();
		mPaint.setAntiAlias(true);
		
		canvas.drawBitmap(bitmapClothes, 0, 0, mPaint);
		canvas.drawBitmap(bitmapFace, 0, 0, mPaint);
		canvas.drawBitmap(bitmapHair, 0, 0, mPaint);
		
		return mBitmap;
	}
    
	//getClipBitmap
	public Bitmap getClipBitmap() {
		BitmapDrawable bd = (BitmapDrawable) getResources().getDrawable(
				R.drawable.beauty);
		Bitmap bitmap = bd.getBitmap();
		
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		Bitmap bm = Bitmap.createBitmap(w, h, Config.ARGB_8888);
		Canvas canvas = new Canvas(bm);
		Paint mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setStyle(Style.STROKE);
		
		canvas.drawBitmap(bitmap, 0, 0, mPaint);
		
		int deltX = 226;
		int deltY = 308;
		
		DashPathEffect dashStyle = new DashPathEffect(new float[] { 10, 5,
				5, 5 }, 2);
		
		RectF faceRect = new RectF(0, 0, 200, 160);
		float [] faceCornerii = new float[] {
				30,30,30,30,75,75,75,75
		};
		mPaint.setColor(0xFF6F8DD5);
		mPaint.setStrokeWidth(6);
		mPaint.setPathEffect(dashStyle);
		Path clip = new Path();
		clip.reset();
		//注意addRoundRect的构造方法的各个参数
		clip.addRoundRect(faceRect, faceCornerii, Direction.CW);
		
		canvas.save();
		canvas.translate(deltX, deltY);
		//注意Region.Op中各种叠加方式的使用
		canvas.clipPath(clip, Region.Op.DIFFERENCE);
		canvas.drawColor(0xDF222222);
		canvas.drawPath(clip, mPaint);
		canvas.restore();
		
		
		Rect srcRect = new Rect(0, 0, 200, 160);
		srcRect.offset(deltX, deltY);
		//为canvas添加DrawFilter
		PaintFlagsDrawFilter dfd = new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG);
		canvas.setDrawFilter(dfd);
		canvas.clipPath(clip);
		canvas.drawBitmap(bitmap, srcRect, faceRect, mPaint);
		
		return bm;
	}

}
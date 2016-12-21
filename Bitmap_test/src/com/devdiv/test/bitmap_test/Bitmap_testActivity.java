package com.devdiv.test.bitmap_test;

/*
 * 1 true��false���������⣬matrixӦ��ʱ
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
//        ע�������Ӧ��Ȩ�ޣ���manifest.xml��
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
  								// ���ж�ȡ
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
    
    /** �����ķ���,����ȡ���ɹ� */  
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
            // apk�����ļ�·��  
            // ����һ��Package ������, �����ص�  
            // ���캯���Ĳ���ֻ��һ��, apk�ļ���·��  
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
            // ���������ʾ�йص�, �����漰��һЩ������ʾ�ȵ�, ����ʹ��Ĭ�ϵ����  
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
            // Ӧ�ó�����Ϣ��, ���������, ������Щ����, ����û����  
            // ApplicationInfo info = mPkgInfo.applicationInfo;  
            Field appInfoFld = pkgParserPkg.getClass().getDeclaredField("applicationInfo");  
            ApplicationInfo info = (ApplicationInfo) appInfoFld.get(pkgParserPkg);  
            // uid ���Ϊ"-1"��ԭ����δ��װ��ϵͳδ������Uid��  
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
            // ������Ƕ�ȡһ��apk�����ͼ��  
            if (info.icon != 0) {  
                Drawable icon = res.getDrawable(info.icon);  
                mImageView2.setImageDrawable(icon);  
            }  
        } catch (Exception e) {  
            e.printStackTrace();  
        }  
    } 
    
   // �ü�
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
//  		//ע��addRoundRect�Ĺ��췽���ĸ�������
////  		clip.addRoundRect(faceRect, faceCornerii, Direction.CW);
//  		
//  		canvas.save();
//  		canvas.translate(deltX, deltY);
//  		//ע��Region.Op�и��ֵ��ӷ�ʽ��ʹ��
//  		canvas.clipPath(clip, Region.Op.DIFFERENCE);
//  		canvas.drawColor(0xDF222222);
//  		canvas.drawPath(clip, mPaint);
//  		canvas.restore();
//  		
//  		
//  		Rect srcRect = new Rect(0, 0, 170, 150);
//  		srcRect.offset(deltX, deltY);
//  		//Ϊcanvas���DrawFilter
//  		PaintFlagsDrawFilter dfd = new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG);
//  		canvas.setDrawFilter(dfd);
//  		canvas.clipPath(clip);
//  		canvas.drawBitmap(bitmap, srcRect, faceRect, mPaint);
  		
  		 
  		return bm;
  	}
    
    //��ȡresĿ¼�µ�drawable��Դ
    public Bitmap getDrawableBitmap() {
    	//��ȡӦ����Դ������ʵ��
    	Resources mResources = getResources();
    	//��ȡdrawable��Դframe��ת��Ϊ BitmapDrawable����
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) mResources.getDrawable(R.drawable.android);
    	//��ȡbitmap
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	
    	return mBitmap;
    }
        
    //��ȡresĿ¼�µ�drawable��Դ
    public Bitmap getResourceBitmap() {
    	Resources mResources = getResources();
    	Bitmap mBitmap = BitmapFactory.decodeResource(mResources, R.drawable.android);
    	
    	return mBitmap;
    }
      
    //��ȡassetsĿ¼�µ�ͼƬ��Դ
    public Bitmap getAssetsBitmap() {
    	//����Bitmap
    	Bitmap mBitmap = null;
    	
    	//��ȡassets��Դ����ʵ��
    	AssetManager mAssetManager = getAssets();
    	
    	try {
    		//��frame.png�ļ���
			InputStream mInputStream = mAssetManager.open("android.png");
			//ͨ��decodeStream���������ļ���
			mBitmap = BitmapFactory.decodeStream(mInputStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	return mBitmap;
    }
    
    //����Bitmap��Դ
    public Bitmap drawGraphics() {
    	//������СΪ320 x 480��ARGB_8888����λͼ
    	Bitmap mBitmap = Bitmap.createBitmap(320, 480, Config.ARGB_8888);
    	//���½���λͼ��Ϊ����
    	Canvas mCanvas = new Canvas(mBitmap);
    	
    	//�Ȼ�һ������
    	mCanvas.drawColor(Color.BLACK);

    	//��������,����������
    	Paint mPaint = new Paint();
    	mPaint.setColor(Color.BLUE);
    	mPaint.setStyle(Style.FILL);
    	
    	Rect mRect = new Rect(10, 10, 300, 80);
    	RectF mRectF = new RectF(mRect);
    	//����Բ�ǰ뾶
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

    //����λͼ��Դ
    //��֪�����ʹ��
    public static void saveBitmap(Bitmap bitmap, String path) {
    	FileOutputStream mFileOutputStream = null;
    	
    	try {
    		File mFile = new File(path);
    		//�����ļ�
			mFile.createNewFile();
			//�����ļ������
			mFileOutputStream = new FileOutputStream(mFile);
			//����Bitmap��PNG�ļ�
			//ͼƬѹ������Ϊ75������PNG��˵��������ᱻ����
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
    
    //Viewת��ΪBitmap
    public void getDrawingCache(final ImageView sourceImageView, final ImageView destImageView) {
    	
    	new Handler().postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				//����bitmap����
				sourceImageView.setDrawingCacheEnabled(true);
				//��ȡbitmap����
				Bitmap mBitmap = sourceImageView.getDrawingCache();
				//��ʾ bitmap
				destImageView.setImageBitmap(mBitmap);
				
//				Bitmap mBitmap = sourceImageView.getDrawingCache();
//				Drawable drawable = (Drawable) new BitmapDrawable(mBitmap);
//				destImageView.setImageDrawable(drawable);
				
				new Handler().postDelayed(new Runnable() {
					
					@Override
					public void run() {
						// TODO Auto-generated method stub
						//������ʾbitmap����
						//destImageView.setImageBitmap(null);
						destImageView.setImageResource(R.drawable.pet);
						
						//ʹ����仰����������һ�仰�Ǵ���ģ���ָ�����
						//destImageView.setBackgroundDrawable(null);
						
						//�ر�bitmap����
						sourceImageView.setDrawingCacheEnabled(false);
						//�ͷ�bitmap������Դ
						sourceImageView.destroyDrawingCache();
					}
				}, DELAY_TIME);
			}
		}, DELAY_TIME);
    }
    
    //ͼƬԲ�Ǵ���
    public Bitmap getRoundedBitmap() {
    	Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.frame);
    	//�����µ�λͼ
    	Bitmap bgBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Config.ARGB_8888);
    	//�Ѵ�����λͼ��Ϊ����
    	Canvas mCanvas = new Canvas(bgBitmap);
    	
    	Paint mPaint = new Paint();
    	Rect mRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
    	RectF mRectF = new RectF(mRect);
    	//����Բ�ǰ뾶Ϊ20
    	float roundPx = 15;
    	mPaint.setAntiAlias(true);
    	//�Ȼ���Բ�Ǿ���
    	mCanvas.drawRoundRect(mRectF, roundPx, roundPx, mPaint);
    	
    	//����ͼ��ĵ���ģʽ
    	mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    	//����ͼ��
    	mCanvas.drawBitmap(mBitmap, mRect, mRect, mPaint);
    	
    	return bgBitmap;
    }
    
    
    //ͼƬ�һ�����
    public Bitmap getGrayBitmap() {
    	Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.android);
    	Bitmap mGrayBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Config.ARGB_8888);
    	Canvas mCanvas = new Canvas(mGrayBitmap);
    	Paint mPaint = new Paint();
    	
    	//������ɫ�任����
    	ColorMatrix mColorMatrix = new ColorMatrix();
    	//���ûҶ�Ӱ�췶Χ
    	mColorMatrix.setSaturation(0);
    	//������ɫ���˾���
    	ColorMatrixColorFilter mColorFilter = new ColorMatrixColorFilter(mColorMatrix);
    	//���û��ʵ���ɫ���˾���
    	mPaint.setColorFilter(mColorFilter);
    	//ʹ�ô����Ļ��ʻ���ͼ��
    	mCanvas.drawBitmap(mBitmap, 0, 0, mPaint);
    	
    	return mGrayBitmap; 	
    }
    
    //��ȡͼ��Alphaλͼ
    public Bitmap getAlphaBitmap() {
    	BitmapDrawable mBitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.enemy_infantry_ninja);
    	Bitmap mBitmap = mBitmapDrawable.getBitmap();
    	
    	//BitmapDrawable��getIntrinsicWidth����������Bitmap��getWidth��������
    	//ע������������������
    	//Bitmap mAlphaBitmap = Bitmap.createBitmap(mBitmapDrawable.getIntrinsicWidth(), mBitmapDrawable.getIntrinsicHeight(), Config.ARGB_8888);
    	Bitmap mAlphaBitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), Config.ARGB_8888);
    	
    	Canvas mCanvas = new Canvas(mAlphaBitmap);
    	Paint mPaint = new Paint();
    	
    	mPaint.setColor(Color.BLUE);
    	//��ԭλͼ����ȡֻ����alpha��λͼ
    	Bitmap alphaBitmap = mBitmap.extractAlpha();
    	//�ڻ����ϣ�mAlphaBitmap������alphaλͼ
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
    	
    	//����ͼ��ľ�������
    	Rect srcRect = new Rect(0, 0, width, height);
    	//����ͼ����ھ�������
    	Rect innerRect = new Rect(srcRect);
    	//����������������
    	innerRect.inset(2, 2);
    	//����ԭʼͼ��
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
		// ͼƬ���ţ�x���Ϊԭ����1����y��Ϊ-1��,ʵ��ͼƬ�ķ�ת
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
		// ͼƬ���ţ�x���Ϊԭ����1����y��Ϊ-1��,ʵ��ͼƬ�ķ�ת
		matrix.preScale(1, -1);
		
		//������ת���ͼƬBitmap����ͼƬ����ԭͼ��һ�롣
		//Bitmap mInverseBitmap = Bitmap.createBitmap(mBitmap, 0, height/2, width, height/2, matrix, false);
		//������׼��Bitmap���󣬿��ԭͼһ�£�����ԭͼ��1.5����
		//ע������createBitmap�Ĳ�ͬ
		//Bitmap mReflectedBitmap = Bitmap.createBitmap(width, height*3/2, Config.ARGB_8888);
		
		Bitmap mInverseBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height, matrix, false);
		Bitmap mReflectedBitmap = Bitmap.createBitmap(width, height*2, Config.ARGB_8888);
		
		// ���½���λͼ��Ϊ����
		Canvas mCanvas = new Canvas(mReflectedBitmap);
		//����ͼƬ
		mCanvas.drawBitmap(mBitmap, 0, 0, null);
		mCanvas.drawBitmap(mInverseBitmap, 0, height, null);
		
		//��ӵ�Ӱ�Ľ���Ч��
		Paint mPaint = new Paint();
		Shader mShader = new LinearGradient(0, height, 0, mReflectedBitmap.getHeight(), 0x70ffffff, 0x00ffffff, TileMode.MIRROR);
		mPaint.setShader(mShader);
		//���õ���ģʽ
		mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
		//��������Ч��
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
		//ע��addRoundRect�Ĺ��췽���ĸ�������
		clip.addRoundRect(faceRect, faceCornerii, Direction.CW);
		
		canvas.save();
		canvas.translate(deltX, deltY);
		//ע��Region.Op�и��ֵ��ӷ�ʽ��ʹ��
		canvas.clipPath(clip, Region.Op.DIFFERENCE);
		canvas.drawColor(0xDF222222);
		canvas.drawPath(clip, mPaint);
		canvas.restore();
		
		
		Rect srcRect = new Rect(0, 0, 200, 160);
		srcRect.offset(deltX, deltY);
		//Ϊcanvas���DrawFilter
		PaintFlagsDrawFilter dfd = new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG);
		canvas.setDrawFilter(dfd);
		canvas.clipPath(clip);
		canvas.drawBitmap(bitmap, srcRect, faceRect, mPaint);
		
		return bm;
	}

}
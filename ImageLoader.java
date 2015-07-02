package com.example.googleplay.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.example.googleplay.R;
import com.example.googleplay.application.utils.DrawableUtils;
import com.example.googleplay.application.utils.FileUtils;
import com.example.googleplay.application.utils.IOUtils;
import com.example.googleplay.application.utils.LogUtils;
import com.example.googleplay.application.utils.StringUtils;
import com.example.googleplay.application.utils.SystemUtils;
import com.example.googleplay.application.utils.UIUtils;
import com.example.googleplay.http.HttpHelper;
import com.example.googleplay.http.HttpHelper.HttpResult;
import com.example.googleplay.manager.ThreadManager;
import com.example.googleplay.manager.ThreadManager.ThreadPoolProxy;

/**
 */
public class ImageLoader {
	/** 鍥剧墖涓嬭浇鐨勭嚎绋嬫睜鍚嶇О */
	public static final String THREAD_POOL_NAME = "IMAGE_THREAD_POOL";
	/** 鍥剧墖缂撳瓨鏈�ぇ鏁伴噺 */
	public static final int MAX_DRAWABLE_COUNT = 100;
	/** 鍥剧墖鐨凨EY缂撳瓨 */
	private static ConcurrentLinkedQueue<String> mKeyCache =  new ConcurrentLinkedQueue<String>();
	/** 鍥剧墖鐨勭紦瀛�*/
	private static Map<String, Drawable> mDrawableCache = new ConcurrentHashMap<String, Drawable>();
	
	private static BitmapFactory.Options mOptions = new BitmapFactory.Options();
	/** 鍥剧墖涓嬭浇鐨勭嚎绋嬫睜 */
	private static ThreadPoolProxy mThreadPool = ThreadManager.getSinglePool(THREAD_POOL_NAME);
	/** 鐢ㄤ簬璁板綍鍥剧墖涓嬭浇鐨勪换鍔★紝浠ヤ究鍙栨秷 */
	private static ConcurrentHashMap<String, Runnable> mMapRuunable = new ConcurrentHashMap<String, Runnable>();
	/** 鍥剧墖鐨勬�澶у皬 */
	private static long mTotalSize;

	static {
		mOptions.inDither = false;// 璁剧疆涓篺alse锛屽皢涓嶈�铏戝浘鐗囩殑鎶栧姩鍊硷紝杩欎細鍑忓皯鍥剧墖鐨勫唴瀛樺崰鐢�		mOptions.inPurgeable = true;// 璁剧疆涓簍ure锛岃〃绀哄厑璁哥郴缁熷湪鍐呭瓨涓嶈冻鏃讹紝鍒犻櫎bitmap鐨勬暟缁勩�
		mOptions.inInputShareable = true;// 鍜宨nPurgeable閰嶅悎浣跨敤锛屽鏋渋nPurgeable鏄痜alse锛岄偅涔堣鍙傛暟灏嗚蹇界暐锛岃〃绀烘槸鍚﹀bitmap鐨勬暟缁勮繘琛屽叡浜�	}

	/** 鍔犺浇鍥剧墖 */
	public static void load(ImageView view, String url) {
		if (view == null || StringUtils.isEmpty(url)) {
			return;
		}
		
		view.setTag(url);//鎶婃帶浠跺拰鍥剧墖鐨剈rl杩涜缁戝畾锛屽洜涓哄姞杞芥槸涓�釜鑰楁椂鐨勶紝绛夊姞杞藉畬姣曚簡闇�鍒ゅ畾璇ユ帶浠舵槸鍚﹀拰璇rl鍖归厤
		
		Drawable drawable = loadFromMemory(url);//浠庡唴瀛樹腑鍔犺浇
		
		if (drawable != null) {
			setImageSafe(view, url, drawable);//濡傛灉鍐呭瓨涓姞杞藉埌浜嗭紝鐩存帴璁剧疆鍥剧墖
		} else {
			view.setImageResource(R.drawable.ic_default);//濡傛灉娌″姞杞藉埌锛岃缃粯璁ゅ浘鐗囷紝骞跺紓姝ュ姞杞�			asyncLoad(view, url);
		}
	}

	/** 寮傛鍔犺浇 */
	private static void asyncLoad(final ImageView view, final String url) {
		//鍏堝垱寤轰竴涓猺unnable灏佽鎵ц杩囩▼
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				//鍏堜粠绾跨▼闃熷垪涓垹闄ゅ綋鍓嶆鍦ㄦ帓闃熺殑绾跨▼浠诲姟锛屼竴鐐硅繘鍏ョ嚎绋嬪氨瑕佸垹闄ゆ帀浜�				mMapRuunable.remove(url);
				//浠庢湰鍦板姞杞藉浘鐗�				Drawable drawable = loadFromLocal(url);
				//濡傛灉鏈湴缂撳瓨鐨勫浘鐗囦负绌猴紝鍒欎粠缃戠粶涓婇潰鍔犺浇
				if (drawable == null) {
					drawable = loadFromNet(url);
				}
				//璁剧疆鎺т欢鐨勫浘鐗�				if (drawable != null) {
					setImageSafe(view, url, drawable);
				}
			}
		};
		cancel(url);//鍏堝彇娑堣繖涓猽rl鐨勪笅杞斤紝鏃犺鏈夋病鏈夊氨鍏堝彇娑堜竴娆�		mMapRuunable.put(url, runnable);//璁颁綇杩欎釜runnable锛屼互渚垮悗闈㈠彇娑�		mThreadPool.execute(runnable);//鎵ц浠诲姟
	}

	/** 鍙栨秷涓嬭浇 */
	public static void cancel(String url) {
		//鏍规嵁url鏉ヨ幏鍙栨寚瀹氱殑runnable
		Runnable runnable = mMapRuunable.remove(url);
		if (runnable != null) {
			//浠庣嚎绋嬫睜涓垹闄よ浠诲姟锛屽鏋滀换鍔″凡缁忓紑濮嬩笅杞戒簡锛屽氨鏃犳硶鍒犻櫎
			mThreadPool.cancel(runnable);
		}
	}

	/** 浠庡唴瀛樹腑鍔犺浇 */
	private static Drawable loadFromMemory(String url) {
		//url鏄痥ey锛屾牴鎹畊rl鍙栧嚭浠庡唴瀛樹腑缂撳瓨鐨勫浘鐗�		Drawable drawable = mDrawableCache.get(url);
		if (drawable != null) {
			//浠庡唴瀛樹腑鑾峰彇鍒颁簡锛岄渶瑕侀噸鏂版斁鍒板唴瀛橀槦鍒楃殑鏈�悗锛屼互渚挎弧瓒矻RC
			//涓�埇缂撳瓨绠楁硶鏈変袱绉嶏紝绗竴鏄疞FU锛屾寜浣跨敤娆℃暟鏉ュ垽瀹氬垹闄や紭鍏堢骇锛屼娇鐢ㄦ鏁版渶灏戠殑鏈�厛鍒犻櫎
			//杩樻湁涓�釜灏辨槸LRC锛屽氨鏄寜鏈�悗浣跨敤鏃堕棿鏉ュ垽瀹氬垹闄や紭鍏堢骇锛屾渶鍚庝娇鐢ㄦ椂闂磋秺鏃╃殑鏈�厛鍒犻櫎
			addDrawableToMemory(url, drawable);
		}
		return drawable;
	}

	/** 浠庢湰鍦拌澶囦腑鍔犺浇 */
	private static Drawable loadFromLocal(String url) {
		Bitmap bitmap = null;
		Drawable drawable = null;
		//鑾风殑缂撳瓨鍥剧墖鐨勭洰褰曪紝濡傛灉鎵嬫満鍐呭瓨鍗′腑鍙互浣跨敤灏辨槸鐢ㄦ墜鏈哄唴瀛樺崱锛屽鏋滄墜鏈哄唴瀛樺彲浠ヤ娇鐢紝灏变娇鐢ㄦ墜鏈篸ata
		String path = FileUtils.getIconDir();
		FileInputStream fis = null;
		try {
			//鑾峰彇娴�			fis = new FileInputStream(new File(path + url));
			if (fis != null) {
				/*鏈湴灏辨槸jni
				 灏介噺涓嶈浣跨敤setImageBitmap鎴杝etImageResource鎴朆itmapFactory.decodeResource鏉ヨ缃竴寮犲ぇ鍥撅紝
				 鍥犱负杩欎簺鍑芥暟鍦ㄥ畬鎴恉ecode鍚庯紝鏈�粓閮芥槸閫氳繃java灞傜殑createBitmap鏉ュ畬鎴愮殑锛岄渶瑕佹秷鑰楁洿澶氬唴瀛樸�
				 鍥犳锛屾敼鐢ㄥ厛閫氳繃BitmapFactory.decodeStream鏂规硶锛屽垱寤哄嚭涓�釜bitmap锛屽啀灏嗗叾璁句负ImageView鐨�source锛�				decodeStream鏈�ぇ鐨勭瀵嗗湪浜庡叾鐩存帴璋冪敤JNI>>nativeDecodeAsset()鏉ュ畬鎴恉ecode锛�				鏃犻渶鍐嶄娇鐢╦ava灞傜殑createBitmap锛屼粠鑰岃妭鐪佷簡java灞傜殑绌洪棿銆� 
				濡傛灉鍦ㄨ鍙栨椂鍔犱笂鍥剧墖鐨凜onfig鍙傛暟锛屽彲浠ヨ窡鏈夋晥鍑忓皯鍔犺浇鐨勫唴瀛橈紝浠庤�璺熸湁鏁堥樆姝㈡姏out of Memory寮傚父
				鍙﹀锛宒ecodeStream鐩存帴鎷跨殑鍥剧墖鏉ヨ鍙栧瓧鑺傜爜浜嗭紝 涓嶄細鏍规嵁鏈哄櫒鐨勫悇绉嶅垎杈ㄧ巼鏉ヨ嚜鍔ㄩ�搴旓紝 
				浣跨敤浜哾ecodeStream涔嬪悗锛岄渶瑕佸湪hdpi鍜宮dpi锛宭dpi涓厤缃浉搴旂殑鍥剧墖璧勬簮锛�
				鍚﹀垯鍦ㄤ笉鍚屽垎杈ㄧ巼鏈哄櫒涓婇兘鏄悓鏍峰ぇ灏忥紙鍍忕礌鐐规暟閲忥級锛屾樉绀哄嚭鏉ョ殑澶у皬灏变笉瀵逛簡銆�				 */
				// BitmapFactory.decodeByteArray(data, offset, length)
				// BitmapFactory.decodeFile(pathName)
				// BitmapFactory.decodeStream(is)
				// 涓婇潰涓変釜鍒嗘瀽婧愮爜鍙煡锛屼粬浠兘鏄湪Java灞傚垱寤篵yte鏁扮粍锛岀劧鍚庢妸鏁版嵁浼犻�缁欐湰鍦颁唬鐮併�
				// 涓嬮潰杩欎釜鏄妸鏂囦欢鎻忚堪绗︿紶閫掔粰鏈湴浠ｇ爜锛岀敱鏈湴浠ｇ爜鍘诲垱寤哄浘鐗�jni搴曞眰
				// 浼樼偣锛岀敱浜庢槸鏈湴浠ｇ爜鍒涘缓鐨勶紝閭ｄ箞byte鏁扮粍鐨勫唴瀛樺崰鐢ㄤ笉浼氱畻鍒板簲鐢ㄥ唴瀛樹腑锛屽苟涓斾竴鏃﹀唴瀛樹笉瓒筹紝灏嗕細鎶奲itmap鐨勬暟缁勫洖鏀舵帀锛岃�bitmap涓嶄細琚洖鏀�				// 褰撴樉绀虹殑鏃跺�锛屽彂鐜癰itmap鐨勬暟缁勪负绌烘椂锛屽皢浼氬啀娆℃牴鎹枃浠舵弿杩扮鍘诲姞杞藉浘鐗囷紝姝ゆ椂鍙兘鐢变簬鍔犺浇鑰楁椂閫犳垚鐣岄潰鍗￠】锛屼絾鎬绘瘮OOM瑕佸ソ寰楀銆�				// 鐢变簬鏈湴浠ｇ爜鍦ㄥ垱寤哄浘鐗囨椂锛屾病鏈夊鍥剧墖杩涜鏍￠獙锛屾墍浠ュ鏋滄枃浠朵笉瀹屾暣锛屾垨鑰呮牴鏈氨涓嶆槸涓�釜鍥剧墖鏃讹紝绯荤粺涔熶笉浼氭姤閿欙紝浠嶇劧浼氳繑鍥炰竴涓猙itmap,浣嗘槸杩欎釜bitmap鏄竴涓函榛戣壊鐨刡itmap銆�				// 鎵�互鎴戜滑鍦ㄤ笅杞藉浘鐗囩殑鏃跺�锛屼竴瀹氳鍏堜互涓�釜涓存椂鏂囦欢涓嬭浇锛岀瓑涓嬭浇瀹屾瘯浜嗭紝鍐嶅鍥剧墖杩涜閲嶅懡鍚嶃�
				bitmap = BitmapFactory.decodeFileDescriptor(fis.getFD(), null, mOptions);
				
			}
			if (null != bitmap) {//鎶奲itmap杞崲鎴恉rawable
				drawable = new BitmapDrawable(UIUtils.getResources(), bitmap);
			}
			if (drawable != null) {//鏀惧埌鍐呭瓨缂撳瓨闃熷垪涓�				addDrawableToMemory(url, drawable);
			}
			//濡傛灉鍙戠敓鍐呭瓨婧㈠嚭锛屽垯娓呮鎵�湁鐨勭紦瀛�		} catch (OutOfMemoryError e) {
			mKeyCache.clear();
			mDrawableCache.clear();
			LogUtils.e(e);
		} catch (Exception e) {
			LogUtils.e(e);
		} finally {
			IOUtils.close(fis);
		}
		return drawable;
	}

	/** 浠庣綉缁滃姞杞藉浘鐗�*/
	private static Drawable loadFromNet(String url) {
		
		HttpResult httpResult = HttpHelper.download(HttpHelper.URL + "image?name=" + url);
		InputStream stream = null;
		if (httpResult == null || (stream = httpResult.getInputStream()) == null) {//璇锋眰缃戠粶
			return null;
		}
		String tempPath = FileUtils.getIconDir() + url + ".temp";
		String path = FileUtils.getIconDir() + url;
		FileUtils.writeFile(stream, tempPath, true);//鎶婄綉缁滀笅杞戒繚瀛樺湪鏈湴
		if (httpResult != null) {//鍏抽棴缃戠粶杩炴帴
			httpResult.close();
		}
		FileUtils.copy(tempPath, path, true);//杩涜鏀瑰悕
		return loadFromLocal(url);//浠庢湰鍦板姞杞�	}

	/** 娣诲姞鍒板唴瀛�*/
	private static void addDrawableToMemory(String url, Drawable drawable) {
		//姣忔娣诲姞鐨勬椂鍊欓兘瑕侀噸鏂扮殑鍒锋柊杩欎釜鍥剧墖鐨勬渶杩戣闂殑鏃堕棿
		mKeyCache.remove(url);
		//鎶婂畠浠庣紦瀛樹腑鍒犻櫎
		mDrawableCache.remove(url);
		//濡傛灉澶т簬绛変簬100寮狅紝鎴栬�鍥剧墖鐨勬�澶у皬澶т簬搴旂敤鎬诲唴瀛樼殑鍥涘垎涔嬩竴鍏堝垹闄ゅ墠闈㈢殑
		while (mKeyCache.size() >= MAX_DRAWABLE_COUNT || mTotalSize >= SystemUtils.getOneAppMaxMemory() / 4) {
			//鍒犻櫎瀛樺偍key鐨勫�锛岃繑鍥炵殑鏄槦鍒楁渶鍓嶉潰鐨剈rl瀛楃涓�
			String firstUrl = mKeyCache.remove();
			//浠庣紦瀛樹腑鍒犻櫎鍦ㄦ渶鍓嶉潰鐨勫浘鐗�			Drawable remove = mDrawableCache.remove(firstUrl);
			//寰楀埌鍒犻櫎鐨勭紦瀛樺浘鐗囩殑澶у皬
			mTotalSize -= DrawableUtils.getDrawableSize(remove);
		}
		mKeyCache.add(url);//娣诲姞锛屾坊鍔犲埌鏈�悗闈�		mDrawableCache.put(url, drawable);//鍚戝浘鐗囩紦瀛樹腑娣诲姞缂撳瓨鐨勫浘鐗�		mTotalSize += DrawableUtils.getDrawableSize(drawable);//寰楀埌鍥剧墖鐨勫ぇ灏忥紝閲嶆柊璧嬪�缁橳otalSize
	}

	/** 璁剧疆缁欐帶浠跺浘鐗�*/
	private static void setImageSafe(final ImageView view, final String url, final Drawable drawable) {
		if (drawable == null && view.getTag() == null) {
			return;
		}
		UIUtils.runInMainThread(new Runnable() {//闇�鍦ㄤ富绾跨▼涓缃�			@Override
			public void run() {
				Object tag;//鍦ㄤ富绾跨▼涓垽鏂紝鍙互鍋氬埌鍚屾
				if ((tag = view.getTag()) != null) {
					String str = (String) tag;
					if (StringUtils.isEquals(str, url)) {//妫�祴濡傛灉url鍜屾帶浠跺尮閰嶏紝
						//鎴戜笅杞界殑姣旇緝鎱紝鐒跺悗褰撹澶嶇敤鐨勬椂鍊欙紝寰堝鏄撴樉绀哄鐢ㄤ互鍓嶇殑鍥剧墖锛�						//鍥犳璁剧疆涓�釜tag锛屽彧鏈夎缃殑tag鍜屽綋鍓嶇殑url鐩哥瓑鐨勬椂鍊欐墠浼氭樉绀哄浘鐗�						//鍙湁澶嶇敤鐨勬椂鍊欐墠浼氬嚭鐜拌繖绉嶆儏鍐�						view.setImageDrawable(drawable);//灏辫繘琛屽浘鐗囪缃�					}
				}
			}
		});
	}
}

1/*
2 * Copyright (C) 2011 The Android Open Source Project
3 *
4 * Licensed under the Apache License, Version 2.0 (the "License");
5 * you may not use this file except in compliance with the License.
6 * You may obtain a copy of the License at
7 *
8 *      http://www.apache.org/licenses/LICENSE-2.0
9 *
10 * Unless required by applicable law or agreed to in writing, software
11 * distributed under the License is distributed on an "AS IS" BASIS,
12 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
13 * See the License for the specific language governing permissions and
14 * limitations under the License.
15 */
16
17package android.support.v4.view;
18
19import android.content.Context;
20import android.content.res.Resources;
21import android.content.res.TypedArray;
22import android.database.DataSetObserver;
23import android.graphics.Canvas;
24import android.graphics.Rect;
25import android.graphics.drawable.Drawable;
26import android.os.Build;
27import android.os.Bundle;
28import android.os.Parcel;
29import android.os.Parcelable;
30import android.os.SystemClock;
31import android.support.v4.os.ParcelableCompat;
32import android.support.v4.os.ParcelableCompatCreatorCallbacks;
33import android.support.v4.view.accessibility.AccessibilityEventCompat;
34import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
35import android.support.v4.view.accessibility.AccessibilityRecordCompat;
36import android.support.v4.widget.EdgeEffectCompat;
37import android.util.AttributeSet;
38import android.util.Log;
39import android.view.FocusFinder;
40import android.view.Gravity;
41import android.view.KeyEvent;
42import android.view.MotionEvent;
43import android.view.SoundEffectConstants;
44import android.view.VelocityTracker;
45import android.view.View;
46import android.view.ViewConfiguration;
47import android.view.ViewGroup;
48import android.view.ViewParent;
49import android.view.accessibility.AccessibilityEvent;
50import android.view.animation.Interpolator;
51import android.widget.Scroller;
52
53import java.lang.reflect.Method;
54import java.util.ArrayList;
55import java.util.Collections;
56import java.util.Comparator;
57
58/**
59 * Layout manager that allows the user to flip left and right
60 * through pages of data.  You supply an implementation of a
61 * {@link PagerAdapter} to generate the pages that the view shows.
62 *
63 * <p>Note this class is currently under early design and
64 * development.  The API will likely change in later updates of
65 * the compatibility library, requiring changes to the source code
66 * of apps when they are compiled against the newer version.</p>
67 *
68 * <p>ViewPager is most often used in conjunction with {@link android.app.Fragment},
69 * which is a convenient way to supply and manage the lifecycle of each page.
70 * There are standard adapters implemented for using fragments with the ViewPager,
71 * which cover the most common use cases.  These are
72 * {@link android.support.v4.app.FragmentPagerAdapter} and
73 * {@link android.support.v4.app.FragmentStatePagerAdapter}; each of these
74 * classes have simple code showing how to build a full user interface
75 * with them.
76 *
77 * <p>Here is a more complicated example of ViewPager, using it in conjuction
78 * with {@link android.app.ActionBar} tabs.  You can find other examples of using
79 * ViewPager in the API 4+ Support Demos and API 13+ Support Demos sample code.
80 *
81 * {@sample development/samples/Support13Demos/src/com/example/android/supportv13/app/ActionBarTabsPager.java
82 *      complete}
83 */
84public class ViewPager extends ViewGroup {
85    private static final String TAG = "ViewPager";
86    private static final boolean DEBUG = false;
87
88    private static final boolean USE_CACHE = false;
89
90    private static final int DEFAULT_OFFSCREEN_PAGES = 1;
91    private static final int MAX_SETTLE_DURATION = 600; // ms
92    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips
93
94    private static final int DEFAULT_GUTTER_SIZE = 16; // dips
95
96    private static final int MIN_FLING_VELOCITY = 400; // dips
97
98    private static final int[] LAYOUT_ATTRS = new int[] {
99        android.R.attr.layout_gravity
100    };
101
102    /**
103     * Used to track what the expected number of items in the adapter should be.
104     * If the app changes this when we don't expect it, we'll throw a big obnoxious exception.
105     */
106    private int mExpectedAdapterCount;
107
108    static class ItemInfo {
109        Object object;
110        int position;
111        boolean scrolling;
112        float widthFactor;
113        float offset;
114    }
115
116    private static final Comparator<ItemInfo> COMPARATOR = new Comparator<ItemInfo>(){
117        @Override
118        public int compare(ItemInfo lhs, ItemInfo rhs) {
119            return lhs.position - rhs.position;
120        }
121    };
122
123    private static final Interpolator sInterpolator = new Interpolator() {
124        public float getInterpolation(float t) {
125            t -= 1.0f;
126            return t * t * t * t * t + 1.0f;
127        }
128    };
129
130    private final ArrayList<ItemInfo> mItems = new ArrayList<ItemInfo>();
131    private final ItemInfo mTempItem = new ItemInfo();
132
133    private final Rect mTempRect = new Rect();
134
135    private PagerAdapter mAdapter;
136    private int mCurItem;   // Index of currently displayed page.
137    private int mRestoredCurItem = -1;
138    private Parcelable mRestoredAdapterState = null;
139    private ClassLoader mRestoredClassLoader = null;
140    private Scroller mScroller;
141    private PagerObserver mObserver;
142
143    private int mPageMargin;
144    private Drawable mMarginDrawable;
145    private int mTopPageBounds;
146    private int mBottomPageBounds;
147
148    // Offsets of the first and last items, if known.
149    // Set during population, used to determine if we are at the beginning
150    // or end of the pager data set during touch scrolling.
151    private float mFirstOffset = -Float.MAX_VALUE;
152    private float mLastOffset = Float.MAX_VALUE;
153
154    private int mChildWidthMeasureSpec;
155    private int mChildHeightMeasureSpec;
156    private boolean mInLayout;
157
158    private boolean mScrollingCacheEnabled;
159
160    private boolean mPopulatePending;
161    private int mOffscreenPageLimit = DEFAULT_OFFSCREEN_PAGES;
162
163    private boolean mIsBeingDragged;
164    private boolean mIsUnableToDrag;
165    private boolean mIgnoreGutter;
166    private int mDefaultGutterSize;
167    private int mGutterSize;
168    private int mTouchSlop;
169    /**
170     * Position of the last motion event.
171     */
172    private float mLastMotionX;
173    private float mLastMotionY;
174    private float mInitialMotionX;
175    private float mInitialMotionY;
176    /**
177     * ID of the active pointer. This is used to retain consistency during
178     * drags/flings if multiple pointers are used.
179     */
180    private int mActivePointerId = INVALID_POINTER;
181    /**
182     * Sentinel value for no current active pointer.
183     * Used by {@link #mActivePointerId}.
184     */
185    private static final int INVALID_POINTER = -1;
186
187    /**
188     * Determines speed during touch scrolling
189     */
190    private VelocityTracker mVelocityTracker;
191    private int mMinimumVelocity;
192    private int mMaximumVelocity;
193    private int mFlingDistance;
194    private int mCloseEnough;
195
196    // If the pager is at least this close to its final position, complete the scroll
197    // on touch down and let the user interact with the content inside instead of
198    // "catching" the flinging pager.
199    private static final int CLOSE_ENOUGH = 2; // dp
200
201    private boolean mFakeDragging;
202    private long mFakeDragBeginTime;
203
204    private EdgeEffectCompat mLeftEdge;
205    private EdgeEffectCompat mRightEdge;
206
207    private boolean mFirstLayout = true;
208    private boolean mNeedCalculatePageOffsets = false;
209    private boolean mCalledSuper;
210    private int mDecorChildCount;
211
212    private OnPageChangeListener mOnPageChangeListener;
213    private OnPageChangeListener mInternalPageChangeListener;
214    private OnAdapterChangeListener mAdapterChangeListener;
215    private PageTransformer mPageTransformer;
216    private Method mSetChildrenDrawingOrderEnabled;
217
218    private static final int DRAW_ORDER_DEFAULT = 0;
219    private static final int DRAW_ORDER_FORWARD = 1;
220    private static final int DRAW_ORDER_REVERSE = 2;
221    private int mDrawingOrder;
222    private ArrayList<View> mDrawingOrderedChildren;
223    private static final ViewPositionComparator sPositionComparator = new ViewPositionComparator();
224
225    /**
226     * Indicates that the pager is in an idle, settled state. The current page
227     * is fully in view and no animation is in progress.
228     */
229    public static final int SCROLL_STATE_IDLE = 0;
230
231    /**
232     * Indicates that the pager is currently being dragged by the user.
233     */
234    public static final int SCROLL_STATE_DRAGGING = 1;
235
236    /**
237     * Indicates that the pager is in the process of settling to a final position.
238     */
239    public static final int SCROLL_STATE_SETTLING = 2;
240
241    private final Runnable mEndScrollRunnable = new Runnable() {
242        public void run() {
243            setScrollState(SCROLL_STATE_IDLE);
244            populate();
245        }
246    };
247
248    private int mScrollState = SCROLL_STATE_IDLE;
249
250    /**
251     * Callback interface for responding to changing state of the selected page.
252     */
253    public interface OnPageChangeListener {
254
255        /**
256         * This method will be invoked when the current page is scrolled, either as part
257         * of a programmatically initiated smooth scroll or a user initiated touch scroll.
258         *
259         * @param position Position index of the first page currently being displayed.
260         *                 Page position+1 will be visible if positionOffset is nonzero.
261         * @param positionOffset Value from [0, 1) indicating the offset from the page at position.
262         * @param positionOffsetPixels Value in pixels indicating the offset from position.
263         */
264        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels);
265
266        /**
267         * This method will be invoked when a new page becomes selected. Animation is not
268         * necessarily complete.
269         *
270         * @param position Position index of the new selected page.
271         */
272        public void onPageSelected(int position);
273
274        /**
275         * Called when the scroll state changes. Useful for discovering when the user
276         * begins dragging, when the pager is automatically settling to the current page,
277         * or when it is fully stopped/idle.
278         *
279         * @param state The new scroll state.
280         * @see ViewPager#SCROLL_STATE_IDLE
281         * @see ViewPager#SCROLL_STATE_DRAGGING
282         * @see ViewPager#SCROLL_STATE_SETTLING
283         */
284        public void onPageScrollStateChanged(int state);
285    }
286
287    /**
288     * Simple implementation of the {@link OnPageChangeListener} interface with stub
289     * implementations of each method. Extend this if you do not intend to override
290     * every method of {@link OnPageChangeListener}.
291     */
292    public static class SimpleOnPageChangeListener implements OnPageChangeListener {
293        @Override
294        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
295            // This space for rent
296        }
297
298        @Override
299        public void onPageSelected(int position) {
300            // This space for rent
301        }
302
303        @Override
304        public void onPageScrollStateChanged(int state) {
305            // This space for rent
306        }
307    }
308
309    /**
310     * A PageTransformer is invoked whenever a visible/attached page is scrolled.
311     * This offers an opportunity for the application to apply a custom transformation
312     * to the page views using animation properties.
313     *
314     * <p>As property animation is only supported as of Android 3.0 and forward,
315     * setting a PageTransformer on a ViewPager on earlier platform versions will
316     * be ignored.</p>
317     */
318    public interface PageTransformer {
319        /**
320         * Apply a property transformation to the given page.
321         *
322         * @param page Apply the transformation to this page
323         * @param position Position of page relative to the current front-and-center
324         *                 position of the pager. 0 is front and center. 1 is one full
325         *                 page position to the right, and -1 is one page position to the left.
326         */
327        public void transformPage(View page, float position);
328    }
329
330    /**
331     * Used internally to monitor when adapters are switched.
332     */
333    interface OnAdapterChangeListener {
334        public void onAdapterChanged(PagerAdapter oldAdapter, PagerAdapter newAdapter);
335    }
336
337    /**
338     * Used internally to tag special types of child views that should be added as
339     * pager decorations by default.
340     */
341    interface Decor {}
342
343    public ViewPager(Context context) {
344        super(context);
345        initViewPager();
346    }
347
348    public ViewPager(Context context, AttributeSet attrs) {
349        super(context, attrs);
350        initViewPager();
351    }
352
353    void initViewPager() {
354        setWillNotDraw(false);
355        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
356        setFocusable(true);
357        final Context context = getContext();
358        mScroller = new Scroller(context, sInterpolator);
359        final ViewConfiguration configuration = ViewConfiguration.get(context);
360        final float density = context.getResources().getDisplayMetrics().density;
361
362        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
363        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
364        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
365        mLeftEdge = new EdgeEffectCompat(context);
366        mRightEdge = new EdgeEffectCompat(context);
367
368        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
369        mCloseEnough = (int) (CLOSE_ENOUGH * density);
370        mDefaultGutterSize = (int) (DEFAULT_GUTTER_SIZE * density);
371
372        ViewCompat.setAccessibilityDelegate(this, new MyAccessibilityDelegate());
373
374        if (ViewCompat.getImportantForAccessibility(this)
375                == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
376            ViewCompat.setImportantForAccessibility(this,
377                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
378        }
379    }
380
381    @Override
382    protected void onDetachedFromWindow() {
383        removeCallbacks(mEndScrollRunnable);
384        super.onDetachedFromWindow();
385    }
386
387    private void setScrollState(int newState) {
388        if (mScrollState == newState) {
389            return;
390        }
391
392        mScrollState = newState;
393        if (mPageTransformer != null) {
394            // PageTransformers can do complex things that benefit from hardware layers.
395            enableLayers(newState != SCROLL_STATE_IDLE);
396        }
397        if (mOnPageChangeListener != null) {
398            mOnPageChangeListener.onPageScrollStateChanged(newState);
399        }
400    }
401
402    /**
403     * Set a PagerAdapter that will supply views for this pager as needed.
404     *
405     * @param adapter Adapter to use
406     */
407    public void setAdapter(PagerAdapter adapter) {
408        if (mAdapter != null) {
409            mAdapter.unregisterDataSetObserver(mObserver);
410            mAdapter.startUpdate(this);
411            for (int i = 0; i < mItems.size(); i++) {
412                final ItemInfo ii = mItems.get(i);
413                mAdapter.destroyItem(this, ii.position, ii.object);
414            }
415            mAdapter.finishUpdate(this);
416            mItems.clear();
417            removeNonDecorViews();
418            mCurItem = 0;
419            scrollTo(0, 0);
420        }
421
422        final PagerAdapter oldAdapter = mAdapter;
423        mAdapter = adapter;
424        mExpectedAdapterCount = 0;
425
426        if (mAdapter != null) {
427            if (mObserver == null) {
428                mObserver = new PagerObserver();
429            }
430            mAdapter.registerDataSetObserver(mObserver);
431            mPopulatePending = false;
432            final boolean wasFirstLayout = mFirstLayout;
433            mFirstLayout = true;
434            mExpectedAdapterCount = mAdapter.getCount();
435            if (mRestoredCurItem >= 0) {
436                mAdapter.restoreState(mRestoredAdapterState, mRestoredClassLoader);
437                setCurrentItemInternal(mRestoredCurItem, false, true);
438                mRestoredCurItem = -1;
439                mRestoredAdapterState = null;
440                mRestoredClassLoader = null;
441            } else if (!wasFirstLayout) {
442                populate();
443            } else {
444                requestLayout();
445            }
446        }
447
448        if (mAdapterChangeListener != null && oldAdapter != adapter) {
449            mAdapterChangeListener.onAdapterChanged(oldAdapter, adapter);
450        }
451    }
452
453    private void removeNonDecorViews() {
454        for (int i = 0; i < getChildCount(); i++) {
455            final View child = getChildAt(i);
456            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
457            if (!lp.isDecor) {
458                removeViewAt(i);
459                i--;
460            }
461        }
462    }
463
464    /**
465     * Retrieve the current adapter supplying pages.
466     *
467     * @return The currently registered PagerAdapter
468     */
469    public PagerAdapter getAdapter() {
470        return mAdapter;
471    }
472
473    void setOnAdapterChangeListener(OnAdapterChangeListener listener) {
474        mAdapterChangeListener = listener;
475    }
476
477    private int getClientWidth() {
478        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
479    }
480
481    /**
482     * Set the currently selected page. If the ViewPager has already been through its first
483     * layout with its current adapter there will be a smooth animated transition between
484     * the current item and the specified item.
485     *
486     * @param item Item index to select
487     */
488    public void setCurrentItem(int item) {
489        mPopulatePending = false;
490        setCurrentItemInternal(item, !mFirstLayout, false);
491    }
492
493    /**
494     * Set the currently selected page.
495     *
496     * @param item Item index to select
497     * @param smoothScroll True to smoothly scroll to the new item, false to transition immediately
498     */
499    public void setCurrentItem(int item, boolean smoothScroll) {
500        mPopulatePending = false;
501        setCurrentItemInternal(item, smoothScroll, false);
502    }
503
504    public int getCurrentItem() {
505        return mCurItem;
506    }
507
508    void setCurrentItemInternal(int item, boolean smoothScroll, boolean always) {
509        setCurrentItemInternal(item, smoothScroll, always, 0);
510    }
511
512    void setCurrentItemInternal(int item, boolean smoothScroll, boolean always, int velocity) {
513        if (mAdapter == null || mAdapter.getCount() <= 0) {
514            setScrollingCacheEnabled(false);
515            return;
516        }
517        if (!always && mCurItem == item && mItems.size() != 0) {
518            setScrollingCacheEnabled(false);
519            return;
520        }
521
522        if (item < 0) {
523            item = 0;
524        } else if (item >= mAdapter.getCount()) {
525            item = mAdapter.getCount() - 1;
526        }
527        final int pageLimit = mOffscreenPageLimit;
528        if (item > (mCurItem + pageLimit) || item < (mCurItem - pageLimit)) {
529            // We are doing a jump by more than one page.  To avoid
530            // glitches, we want to keep all current pages in the view
531            // until the scroll ends.
532            for (int i=0; i<mItems.size(); i++) {
533                mItems.get(i).scrolling = true;
534            }
535        }
536        final boolean dispatchSelected = mCurItem != item;
537
538        if (mFirstLayout) {
539            // We don't have any idea how big we are yet and shouldn't have any pages either.
540            // Just set things up and let the pending layout handle things.
541            mCurItem = item;
542            if (dispatchSelected && mOnPageChangeListener != null) {
543                mOnPageChangeListener.onPageSelected(item);
544            }
545            if (dispatchSelected && mInternalPageChangeListener != null) {
546                mInternalPageChangeListener.onPageSelected(item);
547            }
548            requestLayout();
549        } else {
550            populate(item);
551            scrollToItem(item, smoothScroll, velocity, dispatchSelected);
552        }
553    }
554
555    private void scrollToItem(int item, boolean smoothScroll, int velocity,
556            boolean dispatchSelected) {
557        final ItemInfo curInfo = infoForPosition(item);
558        int destX = 0;
559        if (curInfo != null) {
560            final int width = getClientWidth();
561            destX = (int) (width * Math.max(mFirstOffset,
562                    Math.min(curInfo.offset, mLastOffset)));
563        }
564        if (smoothScroll) {
565            smoothScrollTo(destX, 0, velocity);
566            if (dispatchSelected && mOnPageChangeListener != null) {
567                mOnPageChangeListener.onPageSelected(item);
568            }
569            if (dispatchSelected && mInternalPageChangeListener != null) {
570                mInternalPageChangeListener.onPageSelected(item);
571            }
572        } else {
573            if (dispatchSelected && mOnPageChangeListener != null) {
574                mOnPageChangeListener.onPageSelected(item);
575            }
576            if (dispatchSelected && mInternalPageChangeListener != null) {
577                mInternalPageChangeListener.onPageSelected(item);
578            }
579            completeScroll(false);
580            scrollTo(destX, 0);
581            pageScrolled(destX);
582        }
583    }
584
585    /**
586     * Set a listener that will be invoked whenever the page changes or is incrementally
587     * scrolled. See {@link OnPageChangeListener}.
588     *
589     * @param listener Listener to set
590     */
591    public void setOnPageChangeListener(OnPageChangeListener listener) {
592        mOnPageChangeListener = listener;
593    }
594
595    /**
596     * Set a {@link PageTransformer} that will be called for each attached page whenever
597     * the scroll position is changed. This allows the application to apply custom property
598     * transformations to each page, overriding the default sliding look and feel.
599     *
600     * <p><em>Note:</em> Prior to Android 3.0 the property animation APIs did not exist.
601     * As a result, setting a PageTransformer prior to Android 3.0 (API 11) will have no effect.</p>
602     *
603     * @param reverseDrawingOrder true if the supplied PageTransformer requires page views
604     *                            to be drawn from last to first instead of first to last.
605     * @param transformer PageTransformer that will modify each page's animation properties
606     */
607    public void setPageTransformer(boolean reverseDrawingOrder, PageTransformer transformer) {
608        if (Build.VERSION.SDK_INT >= 11) {
609            final boolean hasTransformer = transformer != null;
610            final boolean needsPopulate = hasTransformer != (mPageTransformer != null);
611            mPageTransformer = transformer;
612            setChildrenDrawingOrderEnabledCompat(hasTransformer);
613            if (hasTransformer) {
614                mDrawingOrder = reverseDrawingOrder ? DRAW_ORDER_REVERSE : DRAW_ORDER_FORWARD;
615            } else {
616                mDrawingOrder = DRAW_ORDER_DEFAULT;
617            }
618            if (needsPopulate) populate();
619        }
620    }
621
622    void setChildrenDrawingOrderEnabledCompat(boolean enable) {
623        if (Build.VERSION.SDK_INT >= 7) {
624            if (mSetChildrenDrawingOrderEnabled == null) {
625                try {
626                    mSetChildrenDrawingOrderEnabled = ViewGroup.class.getDeclaredMethod(
627                            "setChildrenDrawingOrderEnabled", new Class[] { Boolean.TYPE });
628                } catch (NoSuchMethodException e) {
629                    Log.e(TAG, "Can't find setChildrenDrawingOrderEnabled", e);
630                }
631            }
632            try {
633                mSetChildrenDrawingOrderEnabled.invoke(this, enable);
634            } catch (Exception e) {
635                Log.e(TAG, "Error changing children drawing order", e);
636            }
637        }
638    }
639
640    @Override
641    protected int getChildDrawingOrder(int childCount, int i) {
642        final int index = mDrawingOrder == DRAW_ORDER_REVERSE ? childCount - 1 - i : i;
643        final int result = ((LayoutParams) mDrawingOrderedChildren.get(index).getLayoutParams()).childIndex;
644        return result;
645    }
646
647    /**
648     * Set a separate OnPageChangeListener for internal use by the support library.
649     *
650     * @param listener Listener to set
651     * @return The old listener that was set, if any.
652     */
653    OnPageChangeListener setInternalPageChangeListener(OnPageChangeListener listener) {
654        OnPageChangeListener oldListener = mInternalPageChangeListener;
655        mInternalPageChangeListener = listener;
656        return oldListener;
657    }
658
659    /**
660     * Returns the number of pages that will be retained to either side of the
661     * current page in the view hierarchy in an idle state. Defaults to 1.
662     *
663     * @return How many pages will be kept offscreen on either side
664     * @see #setOffscreenPageLimit(int)
665     */
666    public int getOffscreenPageLimit() {
667        return mOffscreenPageLimit;
668    }
669
670    /**
671     * Set the number of pages that should be retained to either side of the
672     * current page in the view hierarchy in an idle state. Pages beyond this
673     * limit will be recreated from the adapter when needed.
674     *
675     * <p>This is offered as an optimization. If you know in advance the number
676     * of pages you will need to support or have lazy-loading mechanisms in place
677     * on your pages, tweaking this setting can have benefits in perceived smoothness
678     * of paging animations and interaction. If you have a small number of pages (3-4)
679     * that you can keep active all at once, less time will be spent in layout for
680     * newly created view subtrees as the user pages back and forth.</p>
681     *
682     * <p>You should keep this limit low, especially if your pages have complex layouts.
683     * This setting defaults to 1.</p>
684     *
685     * @param limit How many pages will be kept offscreen in an idle state.
686     */
687    public void setOffscreenPageLimit(int limit) {
688        if (limit < DEFAULT_OFFSCREEN_PAGES) {
689            Log.w(TAG, "Requested offscreen page limit " + limit + " too small; defaulting to " +
690                    DEFAULT_OFFSCREEN_PAGES);
691            limit = DEFAULT_OFFSCREEN_PAGES;
692        }
693        if (limit != mOffscreenPageLimit) {
694            mOffscreenPageLimit = limit;
695            populate();
696        }
697    }
698
699    /**
700     * Set the margin between pages.
701     *
702     * @param marginPixels Distance between adjacent pages in pixels
703     * @see #getPageMargin()
704     * @see #setPageMarginDrawable(Drawable)
705     * @see #setPageMarginDrawable(int)
706     */
707    public void setPageMargin(int marginPixels) {
708        final int oldMargin = mPageMargin;
709        mPageMargin = marginPixels;
710
711        final int width = getWidth();
712        recomputeScrollPosition(width, width, marginPixels, oldMargin);
713
714        requestLayout();
715    }
716
717    /**
718     * Return the margin between pages.
719     *
720     * @return The size of the margin in pixels
721     */
722    public int getPageMargin() {
723        return mPageMargin;
724    }
725
726    /**
727     * Set a drawable that will be used to fill the margin between pages.
728     *
729     * @param d Drawable to display between pages
730     */
731    public void setPageMarginDrawable(Drawable d) {
732        mMarginDrawable = d;
733        if (d != null) refreshDrawableState();
734        setWillNotDraw(d == null);
735        invalidate();
736    }
737
738    /**
739     * Set a drawable that will be used to fill the margin between pages.
740     *
741     * @param resId Resource ID of a drawable to display between pages
742     */
743    public void setPageMarginDrawable(int resId) {
744        setPageMarginDrawable(getContext().getResources().getDrawable(resId));
745    }
746
747    @Override
748    protected boolean verifyDrawable(Drawable who) {
749        return super.verifyDrawable(who) || who == mMarginDrawable;
750    }
751
752    @Override
753    protected void drawableStateChanged() {
754        super.drawableStateChanged();
755        final Drawable d = mMarginDrawable;
756        if (d != null && d.isStateful()) {
757            d.setState(getDrawableState());
758        }
759    }
760
761    // We want the duration of the page snap animation to be influenced by the distance that
762    // the screen has to travel, however, we don't want this duration to be effected in a
763    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
764    // of travel has on the overall snap duration.
765    float distanceInfluenceForSnapDuration(float f) {
766        f -= 0.5f; // center the values about 0.
767        f *= 0.3f * Math.PI / 2.0f;
768        return (float) Math.sin(f);
769    }
770
771    /**
772     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
773     *
774     * @param x the number of pixels to scroll by on the X axis
775     * @param y the number of pixels to scroll by on the Y axis
776     */
777    void smoothScrollTo(int x, int y) {
778        smoothScrollTo(x, y, 0);
779    }
780
781    /**
782     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
783     *
784     * @param x the number of pixels to scroll by on the X axis
785     * @param y the number of pixels to scroll by on the Y axis
786     * @param velocity the velocity associated with a fling, if applicable. (0 otherwise)
787     */
788    void smoothScrollTo(int x, int y, int velocity) {
789        if (getChildCount() == 0) {
790            // Nothing to do.
791            setScrollingCacheEnabled(false);
792            return;
793        }
794        int sx = getScrollX();
795        int sy = getScrollY();
796        int dx = x - sx;
797        int dy = y - sy;
798        if (dx == 0 && dy == 0) {
799            completeScroll(false);
800            populate();
801            setScrollState(SCROLL_STATE_IDLE);
802            return;
803        }
804
805        setScrollingCacheEnabled(true);
806        setScrollState(SCROLL_STATE_SETTLING);
807
808        final int width = getClientWidth();
809        final int halfWidth = width / 2;
810        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
811        final float distance = halfWidth + halfWidth *
812                distanceInfluenceForSnapDuration(distanceRatio);
813
814        int duration = 0;
815        velocity = Math.abs(velocity);
816        if (velocity > 0) {
817            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
818        } else {
819            final float pageWidth = width * mAdapter.getPageWidth(mCurItem);
820            final float pageDelta = (float) Math.abs(dx) / (pageWidth + mPageMargin);
821            duration = (int) ((pageDelta + 1) * 100);
822        }
823        duration = Math.min(duration, MAX_SETTLE_DURATION);
824
825        mScroller.startScroll(sx, sy, dx, dy, duration);
826        ViewCompat.postInvalidateOnAnimation(this);
827    }
828
829    ItemInfo addNewItem(int position, int index) {
830        ItemInfo ii = new ItemInfo();
831        ii.position = position;
832        ii.object = mAdapter.instantiateItem(this, position);
833        ii.widthFactor = mAdapter.getPageWidth(position);
834        if (index < 0 || index >= mItems.size()) {
835            mItems.add(ii);
836        } else {
837            mItems.add(index, ii);
838        }
839        return ii;
840    }
841
842    void dataSetChanged() {
843        // This method only gets called if our observer is attached, so mAdapter is non-null.
844
845        final int adapterCount = mAdapter.getCount();
846        mExpectedAdapterCount = adapterCount;
847        boolean needPopulate = mItems.size() < mOffscreenPageLimit * 2 + 1 &&
848                mItems.size() < adapterCount;
849        int newCurrItem = mCurItem;
850
851        boolean isUpdating = false;
852        for (int i = 0; i < mItems.size(); i++) {
853            final ItemInfo ii = mItems.get(i);
854            final int newPos = mAdapter.getItemPosition(ii.object);
855
856            if (newPos == PagerAdapter.POSITION_UNCHANGED) {
857                continue;
858            }
859
860            if (newPos == PagerAdapter.POSITION_NONE) {
861                mItems.remove(i);
862                i--;
863
864                if (!isUpdating) {
865                    mAdapter.startUpdate(this);
866                    isUpdating = true;
867                }
868
869                mAdapter.destroyItem(this, ii.position, ii.object);
870                needPopulate = true;
871
872                if (mCurItem == ii.position) {
873                    // Keep the current item in the valid range
874                    newCurrItem = Math.max(0, Math.min(mCurItem, adapterCount - 1));
875                    needPopulate = true;
876                }
877                continue;
878            }
879
880            if (ii.position != newPos) {
881                if (ii.position == mCurItem) {
882                    // Our current item changed position. Follow it.
883                    newCurrItem = newPos;
884                }
885
886                ii.position = newPos;
887                needPopulate = true;
888            }
889        }
890
891        if (isUpdating) {
892            mAdapter.finishUpdate(this);
893        }
894
895        Collections.sort(mItems, COMPARATOR);
896
897        if (needPopulate) {
898            // Reset our known page widths; populate will recompute them.
899            final int childCount = getChildCount();
900            for (int i = 0; i < childCount; i++) {
901                final View child = getChildAt(i);
902                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
903                if (!lp.isDecor) {
904                    lp.widthFactor = 0.f;
905                }
906            }
907
908            setCurrentItemInternal(newCurrItem, false, true);
909            requestLayout();
910        }
911    }
912
913    void populate() {
914        populate(mCurItem);
915    }
916
917    void populate(int newCurrentItem) {
918        ItemInfo oldCurInfo = null;
919        int focusDirection = View.FOCUS_FORWARD;
920        if (mCurItem != newCurrentItem) {
921            focusDirection = mCurItem < newCurrentItem ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
922            oldCurInfo = infoForPosition(mCurItem);
923            mCurItem = newCurrentItem;
924        }
925
926        if (mAdapter == null) {
927            sortChildDrawingOrder();
928            return;
929        }
930
931        // Bail now if we are waiting to populate.  This is to hold off
932        // on creating views from the time the user releases their finger to
933        // fling to a new position until we have finished the scroll to
934        // that position, avoiding glitches from happening at that point.
935        if (mPopulatePending) {
936            if (DEBUG) Log.i(TAG, "populate is pending, skipping for now...");
937            sortChildDrawingOrder();
938            return;
939        }
940
941        // Also, don't populate until we are attached to a window.  This is to
942        // avoid trying to populate before we have restored our view hierarchy
943        // state and conflicting with what is restored.
944        if (getWindowToken() == null) {
945            return;
946        }
947
948        mAdapter.startUpdate(this);
949
950        final int pageLimit = mOffscreenPageLimit;
951        final int startPos = Math.max(0, mCurItem - pageLimit);
952        final int N = mAdapter.getCount();
953        final int endPos = Math.min(N-1, mCurItem + pageLimit);
954
955        if (N != mExpectedAdapterCount) {
956            String resName;
957            try {
958                resName = getResources().getResourceName(getId());
959            } catch (Resources.NotFoundException e) {
960                resName = Integer.toHexString(getId());
961            }
962            throw new IllegalStateException("The application's PagerAdapter changed the adapter's" +
963                    " contents without calling PagerAdapter#notifyDataSetChanged!" +
964                    " Expected adapter item count: " + mExpectedAdapterCount + ", found: " + N +
965                    " Pager id: " + resName +
966                    " Pager class: " + getClass() +
967                    " Problematic adapter: " + mAdapter.getClass());
968        }
969
970        // Locate the currently focused item or add it if needed.
971        int curIndex = -1;
972        ItemInfo curItem = null;
973        for (curIndex = 0; curIndex < mItems.size(); curIndex++) {
974            final ItemInfo ii = mItems.get(curIndex);
975            if (ii.position >= mCurItem) {
976                if (ii.position == mCurItem) curItem = ii;
977                break;
978            }
979        }
980
981        if (curItem == null && N > 0) {
982            curItem = addNewItem(mCurItem, curIndex);
983        }
984
985        // Fill 3x the available width or up to the number of offscreen
986        // pages requested to either side, whichever is larger.
987        // If we have no current item we have no work to do.
988        if (curItem != null) {
989            float extraWidthLeft = 0.f;
990            int itemIndex = curIndex - 1;
991            ItemInfo ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;
992            final int clientWidth = getClientWidth();
993            final float leftWidthNeeded = clientWidth <= 0 ? 0 :
994                    2.f - curItem.widthFactor + (float) getPaddingLeft() / (float) clientWidth;
995            for (int pos = mCurItem - 1; pos >= 0; pos--) {
996                if (extraWidthLeft >= leftWidthNeeded && pos < startPos) {
997                    if (ii == null) {
998                        break;
999                    }
1000                    if (pos == ii.position && !ii.scrolling) {
1001                        mItems.remove(itemIndex);
1002                        mAdapter.destroyItem(this, pos, ii.object);
1003                        if (DEBUG) {
1004                            Log.i(TAG, "populate() - destroyItem() with pos: " + pos +
1005                                    " view: " + ((View) ii.object));
1006                        }
1007                        itemIndex--;
1008                        curIndex--;
1009                        ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;
1010                    }
1011                } else if (ii != null && pos == ii.position) {
1012                    extraWidthLeft += ii.widthFactor;
1013                    itemIndex--;
1014                    ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;
1015                } else {
1016                    ii = addNewItem(pos, itemIndex + 1);
1017                    extraWidthLeft += ii.widthFactor;
1018                    curIndex++;
1019                    ii = itemIndex >= 0 ? mItems.get(itemIndex) : null;
1020                }
1021            }
1022
1023            float extraWidthRight = curItem.widthFactor;
1024            itemIndex = curIndex + 1;
1025            if (extraWidthRight < 2.f) {
1026                ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
1027                final float rightWidthNeeded = clientWidth <= 0 ? 0 :
1028                        (float) getPaddingRight() / (float) clientWidth + 2.f;
1029                for (int pos = mCurItem + 1; pos < N; pos++) {
1030                    if (extraWidthRight >= rightWidthNeeded && pos > endPos) {
1031                        if (ii == null) {
1032                            break;
1033                        }
1034                        if (pos == ii.position && !ii.scrolling) {
1035                            mItems.remove(itemIndex);
1036                            mAdapter.destroyItem(this, pos, ii.object);
1037                            if (DEBUG) {
1038                                Log.i(TAG, "populate() - destroyItem() with pos: " + pos +
1039                                        " view: " + ((View) ii.object));
1040                            }
1041                            ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
1042                        }
1043                    } else if (ii != null && pos == ii.position) {
1044                        extraWidthRight += ii.widthFactor;
1045                        itemIndex++;
1046                        ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
1047                    } else {
1048                        ii = addNewItem(pos, itemIndex);
1049                        itemIndex++;
1050                        extraWidthRight += ii.widthFactor;
1051                        ii = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
1052                    }
1053                }
1054            }
1055
1056            calculatePageOffsets(curItem, curIndex, oldCurInfo);
1057        }
1058
1059        if (DEBUG) {
1060            Log.i(TAG, "Current page list:");
1061            for (int i=0; i<mItems.size(); i++) {
1062                Log.i(TAG, "#" + i + ": page " + mItems.get(i).position);
1063            }
1064        }
1065
1066        mAdapter.setPrimaryItem(this, mCurItem, curItem != null ? curItem.object : null);
1067
1068        mAdapter.finishUpdate(this);
1069
1070        // Check width measurement of current pages and drawing sort order.
1071        // Update LayoutParams as needed.
1072        final int childCount = getChildCount();
1073        for (int i = 0; i < childCount; i++) {
1074            final View child = getChildAt(i);
1075            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1076            lp.childIndex = i;
1077            if (!lp.isDecor && lp.widthFactor == 0.f) {
1078                // 0 means requery the adapter for this, it doesn't have a valid width.
1079                final ItemInfo ii = infoForChild(child);
1080                if (ii != null) {
1081                    lp.widthFactor = ii.widthFactor;
1082                    lp.position = ii.position;
1083                }
1084            }
1085        }
1086        sortChildDrawingOrder();
1087
1088        if (hasFocus()) {
1089            View currentFocused = findFocus();
1090            ItemInfo ii = currentFocused != null ? infoForAnyChild(currentFocused) : null;
1091            if (ii == null || ii.position != mCurItem) {
1092                for (int i=0; i<getChildCount(); i++) {
1093                    View child = getChildAt(i);
1094                    ii = infoForChild(child);
1095                    if (ii != null && ii.position == mCurItem) {
1096                        if (child.requestFocus(focusDirection)) {
1097                            break;
1098                        }
1099                    }
1100                }
1101            }
1102        }
1103    }
1104
1105    private void sortChildDrawingOrder() {
1106        if (mDrawingOrder != DRAW_ORDER_DEFAULT) {
1107            if (mDrawingOrderedChildren == null) {
1108                mDrawingOrderedChildren = new ArrayList<View>();
1109            } else {
1110                mDrawingOrderedChildren.clear();
1111            }
1112            final int childCount = getChildCount();
1113            for (int i = 0; i < childCount; i++) {
1114                final View child = getChildAt(i);
1115                mDrawingOrderedChildren.add(child);
1116            }
1117            Collections.sort(mDrawingOrderedChildren, sPositionComparator);
1118        }
1119    }
1120
1121    private void calculatePageOffsets(ItemInfo curItem, int curIndex, ItemInfo oldCurInfo) {
1122        final int N = mAdapter.getCount();
1123        final int width = getClientWidth();
1124        final float marginOffset = width > 0 ? (float) mPageMargin / width : 0;
1125        // Fix up offsets for later layout.
1126        if (oldCurInfo != null) {
1127            final int oldCurPosition = oldCurInfo.position;
1128            // Base offsets off of oldCurInfo.
1129            if (oldCurPosition < curItem.position) {
1130                int itemIndex = 0;
1131                ItemInfo ii = null;
1132                float offset = oldCurInfo.offset + oldCurInfo.widthFactor + marginOffset;
1133                for (int pos = oldCurPosition + 1;
1134                        pos <= curItem.position && itemIndex < mItems.size(); pos++) {
1135                    ii = mItems.get(itemIndex);
1136                    while (pos > ii.position && itemIndex < mItems.size() - 1) {
1137                        itemIndex++;
1138                        ii = mItems.get(itemIndex);
1139                    }
1140                    while (pos < ii.position) {
1141                        // We don't have an item populated for this,
1142                        // ask the adapter for an offset.
1143                        offset += mAdapter.getPageWidth(pos) + marginOffset;
1144                        pos++;
1145                    }
1146                    ii.offset = offset;
1147                    offset += ii.widthFactor + marginOffset;
1148                }
1149            } else if (oldCurPosition > curItem.position) {
1150                int itemIndex = mItems.size() - 1;
1151                ItemInfo ii = null;
1152                float offset = oldCurInfo.offset;
1153                for (int pos = oldCurPosition - 1;
1154                        pos >= curItem.position && itemIndex >= 0; pos--) {
1155                    ii = mItems.get(itemIndex);
1156                    while (pos < ii.position && itemIndex > 0) {
1157                        itemIndex--;
1158                        ii = mItems.get(itemIndex);
1159                    }
1160                    while (pos > ii.position) {
1161                        // We don't have an item populated for this,
1162                        // ask the adapter for an offset.
1163                        offset -= mAdapter.getPageWidth(pos) + marginOffset;
1164                        pos--;
1165                    }
1166                    offset -= ii.widthFactor + marginOffset;
1167                    ii.offset = offset;
1168                }
1169            }
1170        }
1171
1172        // Base all offsets off of curItem.
1173        final int itemCount = mItems.size();
1174        float offset = curItem.offset;
1175        int pos = curItem.position - 1;
1176        mFirstOffset = curItem.position == 0 ? curItem.offset : -Float.MAX_VALUE;
1177        mLastOffset = curItem.position == N - 1 ?
1178                curItem.offset + curItem.widthFactor - 1 : Float.MAX_VALUE;
1179        // Previous pages
1180        for (int i = curIndex - 1; i >= 0; i--, pos--) {
1181            final ItemInfo ii = mItems.get(i);
1182            while (pos > ii.position) {
1183                offset -= mAdapter.getPageWidth(pos--) + marginOffset;
1184            }
1185            offset -= ii.widthFactor + marginOffset;
1186            ii.offset = offset;
1187            if (ii.position == 0) mFirstOffset = offset;
1188        }
1189        offset = curItem.offset + curItem.widthFactor + marginOffset;
1190        pos = curItem.position + 1;
1191        // Next pages
1192        for (int i = curIndex + 1; i < itemCount; i++, pos++) {
1193            final ItemInfo ii = mItems.get(i);
1194            while (pos < ii.position) {
1195                offset += mAdapter.getPageWidth(pos++) + marginOffset;
1196            }
1197            if (ii.position == N - 1) {
1198                mLastOffset = offset + ii.widthFactor - 1;
1199            }
1200            ii.offset = offset;
1201            offset += ii.widthFactor + marginOffset;
1202        }
1203
1204        mNeedCalculatePageOffsets = false;
1205    }
1206
1207    /**
1208     * This is the persistent state that is saved by ViewPager.  Only needed
1209     * if you are creating a sublass of ViewPager that must save its own
1210     * state, in which case it should implement a subclass of this which
1211     * contains that state.
1212     */
1213    public static class SavedState extends BaseSavedState {
1214        int position;
1215        Parcelable adapterState;
1216        ClassLoader loader;
1217
1218        public SavedState(Parcelable superState) {
1219            super(superState);
1220        }
1221
1222        @Override
1223        public void writeToParcel(Parcel out, int flags) {
1224            super.writeToParcel(out, flags);
1225            out.writeInt(position);
1226            out.writeParcelable(adapterState, flags);
1227        }
1228
1229        @Override
1230        public String toString() {
1231            return "FragmentPager.SavedState{"
1232                    + Integer.toHexString(System.identityHashCode(this))
1233                    + " position=" + position + "}";
1234        }
1235
1236        public static final Parcelable.Creator<SavedState> CREATOR
1237                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
1238                    @Override
1239                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
1240                        return new SavedState(in, loader);
1241                    }
1242                    @Override
1243                    public SavedState[] newArray(int size) {
1244                        return new SavedState[size];
1245                    }
1246                });
1247
1248        SavedState(Parcel in, ClassLoader loader) {
1249            super(in);
1250            if (loader == null) {
1251                loader = getClass().getClassLoader();
1252            }
1253            position = in.readInt();
1254            adapterState = in.readParcelable(loader);
1255            this.loader = loader;
1256        }
1257    }
1258
1259    @Override
1260    public Parcelable onSaveInstanceState() {
1261        Parcelable superState = super.onSaveInstanceState();
1262        SavedState ss = new SavedState(superState);
1263        ss.position = mCurItem;
1264        if (mAdapter != null) {
1265            ss.adapterState = mAdapter.saveState();
1266        }
1267        return ss;
1268    }
1269
1270    @Override
1271    public void onRestoreInstanceState(Parcelable state) {
1272        if (!(state instanceof SavedState)) {
1273            super.onRestoreInstanceState(state);
1274            return;
1275        }
1276
1277        SavedState ss = (SavedState)state;
1278        super.onRestoreInstanceState(ss.getSuperState());
1279
1280        if (mAdapter != null) {
1281            mAdapter.restoreState(ss.adapterState, ss.loader);
1282            setCurrentItemInternal(ss.position, false, true);
1283        } else {
1284            mRestoredCurItem = ss.position;
1285            mRestoredAdapterState = ss.adapterState;
1286            mRestoredClassLoader = ss.loader;
1287        }
1288    }
1289
1290    @Override
1291    public void addView(View child, int index, ViewGroup.LayoutParams params) {
1292        if (!checkLayoutParams(params)) {
1293            params = generateLayoutParams(params);
1294        }
1295        final LayoutParams lp = (LayoutParams) params;
1296        lp.isDecor |= child instanceof Decor;
1297        if (mInLayout) {
1298            if (lp != null && lp.isDecor) {
1299                throw new IllegalStateException("Cannot add pager decor view during layout");
1300            }
1301            lp.needsMeasure = true;
1302            addViewInLayout(child, index, params);
1303        } else {
1304            super.addView(child, index, params);
1305        }
1306
1307        if (USE_CACHE) {
1308            if (child.getVisibility() != GONE) {
1309                child.setDrawingCacheEnabled(mScrollingCacheEnabled);
1310            } else {
1311                child.setDrawingCacheEnabled(false);
1312            }
1313        }
1314    }
1315
1316    @Override
1317    public void removeView(View view) {
1318        if (mInLayout) {
1319            removeViewInLayout(view);
1320        } else {
1321            super.removeView(view);
1322        }
1323    }
1324
1325    ItemInfo infoForChild(View child) {
1326        for (int i=0; i<mItems.size(); i++) {
1327            ItemInfo ii = mItems.get(i);
1328            if (mAdapter.isViewFromObject(child, ii.object)) {
1329                return ii;
1330            }
1331        }
1332        return null;
1333    }
1334
1335    ItemInfo infoForAnyChild(View child) {
1336        ViewParent parent;
1337        while ((parent=child.getParent()) != this) {
1338            if (parent == null || !(parent instanceof View)) {
1339                return null;
1340            }
1341            child = (View)parent;
1342        }
1343        return infoForChild(child);
1344    }
1345
1346    ItemInfo infoForPosition(int position) {
1347        for (int i = 0; i < mItems.size(); i++) {
1348            ItemInfo ii = mItems.get(i);
1349            if (ii.position == position) {
1350                return ii;
1351            }
1352        }
1353        return null;
1354    }
1355
1356    @Override
1357    protected void onAttachedToWindow() {
1358        super.onAttachedToWindow();
1359        mFirstLayout = true;
1360    }
1361
1362    @Override
1363    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
1364        // For simple implementation, our internal size is always 0.
1365        // We depend on the container to specify the layout size of
1366        // our view.  We can't really know what it is since we will be
1367        // adding and removing different arbitrary views and do not
1368        // want the layout to change as this happens.
1369        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
1370                getDefaultSize(0, heightMeasureSpec));
1371
1372        final int measuredWidth = getMeasuredWidth();
1373        final int maxGutterSize = measuredWidth / 10;
1374        mGutterSize = Math.min(maxGutterSize, mDefaultGutterSize);
1375
1376        // Children are just made to fill our space.
1377        int childWidthSize = measuredWidth - getPaddingLeft() - getPaddingRight();
1378        int childHeightSize = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
1379
1380        /*
1381         * Make sure all children have been properly measured. Decor views first.
1382         * Right now we cheat and make this less complicated by assuming decor
1383         * views won't intersect. We will pin to edges based on gravity.
1384         */
1385        int size = getChildCount();
1386        for (int i = 0; i < size; ++i) {
1387            final View child = getChildAt(i);
1388            if (child.getVisibility() != GONE) {
1389                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1390                if (lp != null && lp.isDecor) {
1391                    final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
1392                    final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
1393                    int widthMode = MeasureSpec.AT_MOST;
1394                    int heightMode = MeasureSpec.AT_MOST;
1395                    boolean consumeVertical = vgrav == Gravity.TOP || vgrav == Gravity.BOTTOM;
1396                    boolean consumeHorizontal = hgrav == Gravity.LEFT || hgrav == Gravity.RIGHT;
1397
1398                    if (consumeVertical) {
1399                        widthMode = MeasureSpec.EXACTLY;
1400                    } else if (consumeHorizontal) {
1401                        heightMode = MeasureSpec.EXACTLY;
1402                    }
1403
1404                    int widthSize = childWidthSize;
1405                    int heightSize = childHeightSize;
1406                    if (lp.width != LayoutParams.WRAP_CONTENT) {
1407                        widthMode = MeasureSpec.EXACTLY;
1408                        if (lp.width != LayoutParams.FILL_PARENT) {
1409                            widthSize = lp.width;
1410                        }
1411                    }
1412                    if (lp.height != LayoutParams.WRAP_CONTENT) {
1413                        heightMode = MeasureSpec.EXACTLY;
1414                        if (lp.height != LayoutParams.FILL_PARENT) {
1415                            heightSize = lp.height;
1416                        }
1417                    }
1418                    final int widthSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode);
1419                    final int heightSpec = MeasureSpec.makeMeasureSpec(heightSize, heightMode);
1420                    child.measure(widthSpec, heightSpec);
1421
1422                    if (consumeVertical) {
1423                        childHeightSize -= child.getMeasuredHeight();
1424                    } else if (consumeHorizontal) {
1425                        childWidthSize -= child.getMeasuredWidth();
1426                    }
1427                }
1428            }
1429        }
1430
1431        mChildWidthMeasureSpec = MeasureSpec.makeMeasureSpec(childWidthSize, MeasureSpec.EXACTLY);
1432        mChildHeightMeasureSpec = MeasureSpec.makeMeasureSpec(childHeightSize, MeasureSpec.EXACTLY);
1433
1434        // Make sure we have created all fragments that we need to have shown.
1435        mInLayout = true;
1436        populate();
1437        mInLayout = false;
1438
1439        // Page views next.
1440        size = getChildCount();
1441        for (int i = 0; i < size; ++i) {
1442            final View child = getChildAt(i);
1443            if (child.getVisibility() != GONE) {
1444                if (DEBUG) Log.v(TAG, "Measuring #" + i + " " + child
1445                        + ": " + mChildWidthMeasureSpec);
1446
1447                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1448                if (lp == null || !lp.isDecor) {
1449                    final int widthSpec = MeasureSpec.makeMeasureSpec(
1450                            (int) (childWidthSize * lp.widthFactor), MeasureSpec.EXACTLY);
1451                    child.measure(widthSpec, mChildHeightMeasureSpec);
1452                }
1453            }
1454        }
1455    }
1456
1457    @Override
1458    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
1459        super.onSizeChanged(w, h, oldw, oldh);
1460
1461        // Make sure scroll position is set correctly.
1462        if (w != oldw) {
1463            recomputeScrollPosition(w, oldw, mPageMargin, mPageMargin);
1464        }
1465    }
1466
1467    private void recomputeScrollPosition(int width, int oldWidth, int margin, int oldMargin) {
1468        if (oldWidth > 0 && !mItems.isEmpty()) {
1469            final int widthWithMargin = width - getPaddingLeft() - getPaddingRight() + margin;
1470            final int oldWidthWithMargin = oldWidth - getPaddingLeft() - getPaddingRight()
1471                                           + oldMargin;
1472            final int xpos = getScrollX();
1473            final float pageOffset = (float) xpos / oldWidthWithMargin;
1474            final int newOffsetPixels = (int) (pageOffset * widthWithMargin);
1475
1476            scrollTo(newOffsetPixels, getScrollY());
1477            if (!mScroller.isFinished()) {
1478                // We now return to your regularly scheduled scroll, already in progress.
1479                final int newDuration = mScroller.getDuration() - mScroller.timePassed();
1480                ItemInfo targetInfo = infoForPosition(mCurItem);
1481                mScroller.startScroll(newOffsetPixels, 0,
1482                        (int) (targetInfo.offset * width), 0, newDuration);
1483            }
1484        } else {
1485            final ItemInfo ii = infoForPosition(mCurItem);
1486            final float scrollOffset = ii != null ? Math.min(ii.offset, mLastOffset) : 0;
1487            final int scrollPos = (int) (scrollOffset *
1488                                         (width - getPaddingLeft() - getPaddingRight()));
1489            if (scrollPos != getScrollX()) {
1490                completeScroll(false);
1491                scrollTo(scrollPos, getScrollY());
1492            }
1493        }
1494    }
1495
1496    @Override
1497    protected void onLayout(boolean changed, int l, int t, int r, int b) {
1498        final int count = getChildCount();
1499        int width = r - l;
1500        int height = b - t;
1501        int paddingLeft = getPaddingLeft();
1502        int paddingTop = getPaddingTop();
1503        int paddingRight = getPaddingRight();
1504        int paddingBottom = getPaddingBottom();
1505        final int scrollX = getScrollX();
1506
1507        int decorCount = 0;
1508
1509        // First pass - decor views. We need to do this in two passes so that
1510        // we have the proper offsets for non-decor views later.
1511        for (int i = 0; i < count; i++) {
1512            final View child = getChildAt(i);
1513            if (child.getVisibility() != GONE) {
1514                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1515                int childLeft = 0;
1516                int childTop = 0;
1517                if (lp.isDecor) {
1518                    final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
1519                    final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
1520                    switch (hgrav) {
1521                        default:
1522                            childLeft = paddingLeft;
1523                            break;
1524                        case Gravity.LEFT:
1525                            childLeft = paddingLeft;
1526                            paddingLeft += child.getMeasuredWidth();
1527                            break;
1528                        case Gravity.CENTER_HORIZONTAL:
1529                            childLeft = Math.max((width - child.getMeasuredWidth()) / 2,
1530                                    paddingLeft);
1531                            break;
1532                        case Gravity.RIGHT:
1533                            childLeft = width - paddingRight - child.getMeasuredWidth();
1534                            paddingRight += child.getMeasuredWidth();
1535                            break;
1536                    }
1537                    switch (vgrav) {
1538                        default:
1539                            childTop = paddingTop;
1540                            break;
1541                        case Gravity.TOP:
1542                            childTop = paddingTop;
1543                            paddingTop += child.getMeasuredHeight();
1544                            break;
1545                        case Gravity.CENTER_VERTICAL:
1546                            childTop = Math.max((height - child.getMeasuredHeight()) / 2,
1547                                    paddingTop);
1548                            break;
1549                        case Gravity.BOTTOM:
1550                            childTop = height - paddingBottom - child.getMeasuredHeight();
1551                            paddingBottom += child.getMeasuredHeight();
1552                            break;
1553                    }
1554                    childLeft += scrollX;
1555                    child.layout(childLeft, childTop,
1556                            childLeft + child.getMeasuredWidth(),
1557                            childTop + child.getMeasuredHeight());
1558                    decorCount++;
1559                }
1560            }
1561        }
1562
1563        final int childWidth = width - paddingLeft - paddingRight;
1564        // Page views. Do this once we have the right padding offsets from above.
1565        for (int i = 0; i < count; i++) {
1566            final View child = getChildAt(i);
1567            if (child.getVisibility() != GONE) {
1568                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1569                ItemInfo ii;
1570                if (!lp.isDecor && (ii = infoForChild(child)) != null) {
1571                    int loff = (int) (childWidth * ii.offset);
1572                    int childLeft = paddingLeft + loff;
1573                    int childTop = paddingTop;
1574                    if (lp.needsMeasure) {
1575                        // This was added during layout and needs measurement.
1576                        // Do it now that we know what we're working with.
1577                        lp.needsMeasure = false;
1578                        final int widthSpec = MeasureSpec.makeMeasureSpec(
1579                                (int) (childWidth * lp.widthFactor),
1580                                MeasureSpec.EXACTLY);
1581                        final int heightSpec = MeasureSpec.makeMeasureSpec(
1582                                (int) (height - paddingTop - paddingBottom),
1583                                MeasureSpec.EXACTLY);
1584                        child.measure(widthSpec, heightSpec);
1585                    }
1586                    if (DEBUG) Log.v(TAG, "Positioning #" + i + " " + child + " f=" + ii.object
1587                            + ":" + childLeft + "," + childTop + " " + child.getMeasuredWidth()
1588                            + "x" + child.getMeasuredHeight());
1589                    child.layout(childLeft, childTop,
1590                            childLeft + child.getMeasuredWidth(),
1591                            childTop + child.getMeasuredHeight());
1592                }
1593            }
1594        }
1595        mTopPageBounds = paddingTop;
1596        mBottomPageBounds = height - paddingBottom;
1597        mDecorChildCount = decorCount;
1598
1599        if (mFirstLayout) {
1600            scrollToItem(mCurItem, false, 0, false);
1601        }
1602        mFirstLayout = false;
1603    }
1604
1605    @Override
1606    public void computeScroll() {
1607        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
1608            int oldX = getScrollX();
1609            int oldY = getScrollY();
1610            int x = mScroller.getCurrX();
1611            int y = mScroller.getCurrY();
1612
1613            if (oldX != x || oldY != y) {
1614                scrollTo(x, y);
1615                if (!pageScrolled(x)) {
1616                    mScroller.abortAnimation();
1617                    scrollTo(0, y);
1618                }
1619            }
1620
1621            // Keep on drawing until the animation has finished.
1622            ViewCompat.postInvalidateOnAnimation(this);
1623            return;
1624        }
1625
1626        // Done with scroll, clean up state.
1627        completeScroll(true);
1628    }
1629
1630    private boolean pageScrolled(int xpos) {
1631        if (mItems.size() == 0) {
1632            mCalledSuper = false;
1633            onPageScrolled(0, 0, 0);
1634            if (!mCalledSuper) {
1635                throw new IllegalStateException(
1636                        "onPageScrolled did not call superclass implementation");
1637            }
1638            return false;
1639        }
1640        final ItemInfo ii = infoForCurrentScrollPosition();
1641        final int width = getClientWidth();
1642        final int widthWithMargin = width + mPageMargin;
1643        final float marginOffset = (float) mPageMargin / width;
1644        final int currentPage = ii.position;
1645        final float pageOffset = (((float) xpos / width) - ii.offset) /
1646                (ii.widthFactor + marginOffset);
1647        final int offsetPixels = (int) (pageOffset * widthWithMargin);
1648
1649        mCalledSuper = false;
1650        onPageScrolled(currentPage, pageOffset, offsetPixels);
1651        if (!mCalledSuper) {
1652            throw new IllegalStateException(
1653                    "onPageScrolled did not call superclass implementation");
1654        }
1655        return true;
1656    }
1657
1658    /**
1659     * This method will be invoked when the current page is scrolled, either as part
1660     * of a programmatically initiated smooth scroll or a user initiated touch scroll.
1661     * If you override this method you must call through to the superclass implementation
1662     * (e.g. super.onPageScrolled(position, offset, offsetPixels)) before onPageScrolled
1663     * returns.
1664     *
1665     * @param position Position index of the first page currently being displayed.
1666     *                 Page position+1 will be visible if positionOffset is nonzero.
1667     * @param offset Value from [0, 1) indicating the offset from the page at position.
1668     * @param offsetPixels Value in pixels indicating the offset from position.
1669     */
1670    protected void onPageScrolled(int position, float offset, int offsetPixels) {
1671        // Offset any decor views if needed - keep them on-screen at all times.
1672        if (mDecorChildCount > 0) {
1673            final int scrollX = getScrollX();
1674            int paddingLeft = getPaddingLeft();
1675            int paddingRight = getPaddingRight();
1676            final int width = getWidth();
1677            final int childCount = getChildCount();
1678            for (int i = 0; i < childCount; i++) {
1679                final View child = getChildAt(i);
1680                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1681                if (!lp.isDecor) continue;
1682
1683                final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
1684                int childLeft = 0;
1685                switch (hgrav) {
1686                    default:
1687                        childLeft = paddingLeft;
1688                        break;
1689                    case Gravity.LEFT:
1690                        childLeft = paddingLeft;
1691                        paddingLeft += child.getWidth();
1692                        break;
1693                    case Gravity.CENTER_HORIZONTAL:
1694                        childLeft = Math.max((width - child.getMeasuredWidth()) / 2,
1695                                paddingLeft);
1696                        break;
1697                    case Gravity.RIGHT:
1698                        childLeft = width - paddingRight - child.getMeasuredWidth();
1699                        paddingRight += child.getMeasuredWidth();
1700                        break;
1701                }
1702                childLeft += scrollX;
1703
1704                final int childOffset = childLeft - child.getLeft();
1705                if (childOffset != 0) {
1706                    child.offsetLeftAndRight(childOffset);
1707                }
1708            }
1709        }
1710
1711        if (mOnPageChangeListener != null) {
1712            mOnPageChangeListener.onPageScrolled(position, offset, offsetPixels);
1713        }
1714        if (mInternalPageChangeListener != null) {
1715            mInternalPageChangeListener.onPageScrolled(position, offset, offsetPixels);
1716        }
1717
1718        if (mPageTransformer != null) {
1719            final int scrollX = getScrollX();
1720            final int childCount = getChildCount();
1721            for (int i = 0; i < childCount; i++) {
1722                final View child = getChildAt(i);
1723                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
1724
1725                if (lp.isDecor) continue;
1726
1727                final float transformPos = (float) (child.getLeft() - scrollX) / getClientWidth();
1728                mPageTransformer.transformPage(child, transformPos);
1729            }
1730        }
1731
1732        mCalledSuper = true;
1733    }
1734
1735    private void completeScroll(boolean postEvents) {
1736        boolean needPopulate = mScrollState == SCROLL_STATE_SETTLING;
1737        if (needPopulate) {
1738            // Done with scroll, no longer want to cache view drawing.
1739            setScrollingCacheEnabled(false);
1740            mScroller.abortAnimation();
1741            int oldX = getScrollX();
1742            int oldY = getScrollY();
1743            int x = mScroller.getCurrX();
1744            int y = mScroller.getCurrY();
1745            if (oldX != x || oldY != y) {
1746                scrollTo(x, y);
1747            }
1748        }
1749        mPopulatePending = false;
1750        for (int i=0; i<mItems.size(); i++) {
1751            ItemInfo ii = mItems.get(i);
1752            if (ii.scrolling) {
1753                needPopulate = true;
1754                ii.scrolling = false;
1755            }
1756        }
1757        if (needPopulate) {
1758            if (postEvents) {
1759                ViewCompat.postOnAnimation(this, mEndScrollRunnable);
1760            } else {
1761                mEndScrollRunnable.run();
1762            }
1763        }
1764    }
1765
1766    private boolean isGutterDrag(float x, float dx) {
1767        return (x < mGutterSize && dx > 0) || (x > getWidth() - mGutterSize && dx < 0);
1768    }
1769
1770    private void enableLayers(boolean enable) {
1771        final int childCount = getChildCount();
1772        for (int i = 0; i < childCount; i++) {
1773            final int layerType = enable ?
1774                    ViewCompat.LAYER_TYPE_HARDWARE : ViewCompat.LAYER_TYPE_NONE;
1775            ViewCompat.setLayerType(getChildAt(i), layerType, null);
1776        }
1777    }
1778
1779    @Override
1780    public boolean onInterceptTouchEvent(MotionEvent ev) {
1781        /*
1782         * This method JUST determines whether we want to intercept the motion.
1783         * If we return true, onMotionEvent will be called and we do the actual
1784         * scrolling there.
1785         */
1786
1787        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
1788
1789        // Always take care of the touch gesture being complete.
1790        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
1791            // Release the drag.
1792            if (DEBUG) Log.v(TAG, "Intercept done!");
1793            mIsBeingDragged = false;
1794            mIsUnableToDrag = false;
1795            mActivePointerId = INVALID_POINTER;
1796            if (mVelocityTracker != null) {
1797                mVelocityTracker.recycle();
1798                mVelocityTracker = null;
1799            }
1800            return false;
1801        }
1802
1803        // Nothing more to do here if we have decided whether or not we
1804        // are dragging.
1805        if (action != MotionEvent.ACTION_DOWN) {
1806            if (mIsBeingDragged) {
1807                if (DEBUG) Log.v(TAG, "Intercept returning true!");
1808                return true;
1809            }
1810            if (mIsUnableToDrag) {
1811                if (DEBUG) Log.v(TAG, "Intercept returning false!");
1812                return false;
1813            }
1814        }
1815
1816        switch (action) {
1817            case MotionEvent.ACTION_MOVE: {
1818                /*
1819                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
1820                 * whether the user has moved far enough from his original down touch.
1821                 */
1822
1823                /*
1824                * Locally do absolute value. mLastMotionY is set to the y value
1825                * of the down event.
1826                */
1827                final int activePointerId = mActivePointerId;
1828                if (activePointerId == INVALID_POINTER) {
1829                    // If we don't have a valid id, the touch down wasn't on content.
1830                    break;
1831                }
1832
1833                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
1834                final float x = MotionEventCompat.getX(ev, pointerIndex);
1835                final float dx = x - mLastMotionX;
1836                final float xDiff = Math.abs(dx);
1837                final float y = MotionEventCompat.getY(ev, pointerIndex);
1838                final float yDiff = Math.abs(y - mInitialMotionY);
1839                if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
1840
1841                if (dx != 0 && !isGutterDrag(mLastMotionX, dx) &&
1842                        canScroll(this, false, (int) dx, (int) x, (int) y)) {
1843                    // Nested view has scrollable area under this point. Let it be handled there.
1844                    mLastMotionX = x;
1845                    mLastMotionY = y;
1846                    mIsUnableToDrag = true;
1847                    return false;
1848                }
1849                if (xDiff > mTouchSlop && xDiff * 0.5f > yDiff) {
1850                    if (DEBUG) Log.v(TAG, "Starting drag!");
1851                    mIsBeingDragged = true;
1852                    requestParentDisallowInterceptTouchEvent(true);
1853                    setScrollState(SCROLL_STATE_DRAGGING);
1854                    mLastMotionX = dx > 0 ? mInitialMotionX + mTouchSlop :
1855                            mInitialMotionX - mTouchSlop;
1856                    mLastMotionY = y;
1857                    setScrollingCacheEnabled(true);
1858                } else if (yDiff > mTouchSlop) {
1859                    // The finger has moved enough in the vertical
1860                    // direction to be counted as a drag...  abort
1861                    // any attempt to drag horizontally, to work correctly
1862                    // with children that have scrolling containers.
1863                    if (DEBUG) Log.v(TAG, "Starting unable to drag!");
1864                    mIsUnableToDrag = true;
1865                }
1866                if (mIsBeingDragged) {
1867                    // Scroll to follow the motion event
1868                    if (performDrag(x)) {
1869                        ViewCompat.postInvalidateOnAnimation(this);
1870                    }
1871                }
1872                break;
1873            }
1874
1875            case MotionEvent.ACTION_DOWN: {
1876                /*
1877                 * Remember location of down touch.
1878                 * ACTION_DOWN always refers to pointer index 0.
1879                 */
1880                mLastMotionX = mInitialMotionX = ev.getX();
1881                mLastMotionY = mInitialMotionY = ev.getY();
1882                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
1883                mIsUnableToDrag = false;
1884
1885                mScroller.computeScrollOffset();
1886                if (mScrollState == SCROLL_STATE_SETTLING &&
1887                        Math.abs(mScroller.getFinalX() - mScroller.getCurrX()) > mCloseEnough) {
1888                    // Let the user 'catch' the pager as it animates.
1889                    mScroller.abortAnimation();
1890                    mPopulatePending = false;
1891                    populate();
1892                    mIsBeingDragged = true;
1893                    requestParentDisallowInterceptTouchEvent(true);
1894                    setScrollState(SCROLL_STATE_DRAGGING);
1895                } else {
1896                    completeScroll(false);
1897                    mIsBeingDragged = false;
1898                }
1899
1900                if (DEBUG) Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
1901                        + " mIsBeingDragged=" + mIsBeingDragged
1902                        + "mIsUnableToDrag=" + mIsUnableToDrag);
1903                break;
1904            }
1905
1906            case MotionEventCompat.ACTION_POINTER_UP:
1907                onSecondaryPointerUp(ev);
1908                break;
1909        }
1910
1911        if (mVelocityTracker == null) {
1912            mVelocityTracker = VelocityTracker.obtain();
1913        }
1914        mVelocityTracker.addMovement(ev);
1915
1916        /*
1917         * The only time we want to intercept motion events is if we are in the
1918         * drag mode.
1919         */
1920        return mIsBeingDragged;
1921    }
1922
1923    @Override
1924    public boolean onTouchEvent(MotionEvent ev) {
1925        if (mFakeDragging) {
1926            // A fake drag is in progress already, ignore this real one
1927            // but still eat the touch events.
1928            // (It is likely that the user is multi-touching the screen.)
1929            return true;
1930        }
1931
1932        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
1933            // Don't handle edge touches immediately -- they may actually belong to one of our
1934            // descendants.
1935            return false;
1936        }
1937
1938        if (mAdapter == null || mAdapter.getCount() == 0) {
1939            // Nothing to present or scroll; nothing to touch.
1940            return false;
1941        }
1942
1943        if (mVelocityTracker == null) {
1944            mVelocityTracker = VelocityTracker.obtain();
1945        }
1946        mVelocityTracker.addMovement(ev);
1947
1948        final int action = ev.getAction();
1949        boolean needsInvalidate = false;
1950
1951        switch (action & MotionEventCompat.ACTION_MASK) {
1952            case MotionEvent.ACTION_DOWN: {
1953                mScroller.abortAnimation();
1954                mPopulatePending = false;
1955                populate();
1956
1957                // Remember where the motion event started
1958                mLastMotionX = mInitialMotionX = ev.getX();
1959                mLastMotionY = mInitialMotionY = ev.getY();
1960                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
1961                break;
1962            }
1963            case MotionEvent.ACTION_MOVE:
1964                if (!mIsBeingDragged) {
1965                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
1966                    final float x = MotionEventCompat.getX(ev, pointerIndex);
1967                    final float xDiff = Math.abs(x - mLastMotionX);
1968                    final float y = MotionEventCompat.getY(ev, pointerIndex);
1969                    final float yDiff = Math.abs(y - mLastMotionY);
1970                    if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
1971                    if (xDiff > mTouchSlop && xDiff > yDiff) {
1972                        if (DEBUG) Log.v(TAG, "Starting drag!");
1973                        mIsBeingDragged = true;
1974                        requestParentDisallowInterceptTouchEvent(true);
1975                        mLastMotionX = x - mInitialMotionX > 0 ? mInitialMotionX + mTouchSlop :
1976                                mInitialMotionX - mTouchSlop;
1977                        mLastMotionY = y;
1978                        setScrollState(SCROLL_STATE_DRAGGING);
1979                        setScrollingCacheEnabled(true);
1980
1981                        // Disallow Parent Intercept, just in case
1982                        ViewParent parent = getParent();
1983                        if (parent != null) {
1984                            parent.requestDisallowInterceptTouchEvent(true);
1985                        }
1986                    }
1987                }
1988                // Not else! Note that mIsBeingDragged can be set above.
1989                if (mIsBeingDragged) {
1990                    // Scroll to follow the motion event
1991                    final int activePointerIndex = MotionEventCompat.findPointerIndex(
1992                            ev, mActivePointerId);
1993                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
1994                    needsInvalidate |= performDrag(x);
1995                }
1996                break;
1997            case MotionEvent.ACTION_UP:
1998                if (mIsBeingDragged) {
1999                    final VelocityTracker velocityTracker = mVelocityTracker;
2000                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
2001                    int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(
2002                            velocityTracker, mActivePointerId);
2003                    mPopulatePending = true;
2004                    final int width = getClientWidth();
2005                    final int scrollX = getScrollX();
2006                    final ItemInfo ii = infoForCurrentScrollPosition();
2007                    final int currentPage = ii.position;
2008                    final float pageOffset = (((float) scrollX / width) - ii.offset) / ii.widthFactor;
2009                    final int activePointerIndex =
2010                            MotionEventCompat.findPointerIndex(ev, mActivePointerId);
2011                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
2012                    final int totalDelta = (int) (x - mInitialMotionX);
2013                    int nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity,
2014                            totalDelta);
2015                    setCurrentItemInternal(nextPage, true, true, initialVelocity);
2016
2017                    mActivePointerId = INVALID_POINTER;
2018                    endDrag();
2019                    needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
2020                }
2021                break;
2022            case MotionEvent.ACTION_CANCEL:
2023                if (mIsBeingDragged) {
2024                    scrollToItem(mCurItem, true, 0, false);
2025                    mActivePointerId = INVALID_POINTER;
2026                    endDrag();
2027                    needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
2028                }
2029                break;
2030            case MotionEventCompat.ACTION_POINTER_DOWN: {
2031                final int index = MotionEventCompat.getActionIndex(ev);
2032                final float x = MotionEventCompat.getX(ev, index);
2033                mLastMotionX = x;
2034                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
2035                break;
2036            }
2037            case MotionEventCompat.ACTION_POINTER_UP:
2038                onSecondaryPointerUp(ev);
2039                mLastMotionX = MotionEventCompat.getX(ev,
2040                        MotionEventCompat.findPointerIndex(ev, mActivePointerId));
2041                break;
2042        }
2043        if (needsInvalidate) {
2044            ViewCompat.postInvalidateOnAnimation(this);
2045        }
2046        return true;
2047    }
2048
2049    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
2050        final ViewParent parent = getParent();
2051        if (parent != null) {
2052            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
2053        }
2054    }
2055
2056    private boolean performDrag(float x) {
2057        boolean needsInvalidate = false;
2058
2059        final float deltaX = mLastMotionX - x;
2060        mLastMotionX = x;
2061
2062        float oldScrollX = getScrollX();
2063        float scrollX = oldScrollX + deltaX;
2064        final int width = getClientWidth();
2065
2066        float leftBound = width * mFirstOffset;
2067        float rightBound = width * mLastOffset;
2068        boolean leftAbsolute = true;
2069        boolean rightAbsolute = true;
2070
2071        final ItemInfo firstItem = mItems.get(0);
2072        final ItemInfo lastItem = mItems.get(mItems.size() - 1);
2073        if (firstItem.position != 0) {
2074            leftAbsolute = false;
2075            leftBound = firstItem.offset * width;
2076        }
2077        if (lastItem.position != mAdapter.getCount() - 1) {
2078            rightAbsolute = false;
2079            rightBound = lastItem.offset * width;
2080        }
2081
2082        if (scrollX < leftBound) {
2083            if (leftAbsolute) {
2084                float over = leftBound - scrollX;
2085                needsInvalidate = mLeftEdge.onPull(Math.abs(over) / width);
2086            }
2087            scrollX = leftBound;
2088        } else if (scrollX > rightBound) {
2089            if (rightAbsolute) {
2090                float over = scrollX - rightBound;
2091                needsInvalidate = mRightEdge.onPull(Math.abs(over) / width);
2092            }
2093            scrollX = rightBound;
2094        }
2095        // Don't lose the rounded component
2096        mLastMotionX += scrollX - (int) scrollX;
2097        scrollTo((int) scrollX, getScrollY());
2098        pageScrolled((int) scrollX);
2099
2100        return needsInvalidate;
2101    }
2102
2103    /**
2104     * @return Info about the page at the current scroll position.
2105     *         This can be synthetic for a missing middle page; the 'object' field can be null.
2106     */
2107    private ItemInfo infoForCurrentScrollPosition() {
2108        final int width = getClientWidth();
2109        final float scrollOffset = width > 0 ? (float) getScrollX() / width : 0;
2110        final float marginOffset = width > 0 ? (float) mPageMargin / width : 0;
2111        int lastPos = -1;
2112        float lastOffset = 0.f;
2113        float lastWidth = 0.f;
2114        boolean first = true;
2115
2116        ItemInfo lastItem = null;
2117        for (int i = 0; i < mItems.size(); i++) {
2118            ItemInfo ii = mItems.get(i);
2119            float offset;
2120            if (!first && ii.position != lastPos + 1) {
2121                // Create a synthetic item for a missing page.
2122                ii = mTempItem;
2123                ii.offset = lastOffset + lastWidth + marginOffset;
2124                ii.position = lastPos + 1;
2125                ii.widthFactor = mAdapter.getPageWidth(ii.position);
2126                i--;
2127            }
2128            offset = ii.offset;
2129
2130            final float leftBound = offset;
2131            final float rightBound = offset + ii.widthFactor + marginOffset;
2132            if (first || scrollOffset >= leftBound) {
2133                if (scrollOffset < rightBound || i == mItems.size() - 1) {
2134                    return ii;
2135                }
2136            } else {
2137                return lastItem;
2138            }
2139            first = false;
2140            lastPos = ii.position;
2141            lastOffset = offset;
2142            lastWidth = ii.widthFactor;
2143            lastItem = ii;
2144        }
2145
2146        return lastItem;
2147    }
2148
2149    private int determineTargetPage(int currentPage, float pageOffset, int velocity, int deltaX) {
2150        int targetPage;
2151        if (Math.abs(deltaX) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity) {
2152            targetPage = velocity > 0 ? currentPage : currentPage + 1;
2153        } else {
2154            final float truncator = currentPage >= mCurItem ? 0.4f : 0.6f;
2155            targetPage = (int) (currentPage + pageOffset + truncator);
2156        }
2157
2158        if (mItems.size() > 0) {
2159            final ItemInfo firstItem = mItems.get(0);
2160            final ItemInfo lastItem = mItems.get(mItems.size() - 1);
2161
2162            // Only let the user target pages we have items for
2163            targetPage = Math.max(firstItem.position, Math.min(targetPage, lastItem.position));
2164        }
2165
2166        return targetPage;
2167    }
2168
2169    @Override
2170    public void draw(Canvas canvas) {
2171        super.draw(canvas);
2172        boolean needsInvalidate = false;
2173
2174        final int overScrollMode = ViewCompat.getOverScrollMode(this);
2175        if (overScrollMode == ViewCompat.OVER_SCROLL_ALWAYS ||
2176                (overScrollMode == ViewCompat.OVER_SCROLL_IF_CONTENT_SCROLLS &&
2177                        mAdapter != null && mAdapter.getCount() > 1)) {
2178            if (!mLeftEdge.isFinished()) {
2179                final int restoreCount = canvas.save();
2180                final int height = getHeight() - getPaddingTop() - getPaddingBottom();
2181                final int width = getWidth();
2182
2183                canvas.rotate(270);
2184                canvas.translate(-height + getPaddingTop(), mFirstOffset * width);
2185                mLeftEdge.setSize(height, width);
2186                needsInvalidate |= mLeftEdge.draw(canvas);
2187                canvas.restoreToCount(restoreCount);
2188            }
2189            if (!mRightEdge.isFinished()) {
2190                final int restoreCount = canvas.save();
2191                final int width = getWidth();
2192                final int height = getHeight() - getPaddingTop() - getPaddingBottom();
2193
2194                canvas.rotate(90);
2195                canvas.translate(-getPaddingTop(), -(mLastOffset + 1) * width);
2196                mRightEdge.setSize(height, width);
2197                needsInvalidate |= mRightEdge.draw(canvas);
2198                canvas.restoreToCount(restoreCount);
2199            }
2200        } else {
2201            mLeftEdge.finish();
2202            mRightEdge.finish();
2203        }
2204
2205        if (needsInvalidate) {
2206            // Keep animating
2207            ViewCompat.postInvalidateOnAnimation(this);
2208        }
2209    }
2210
2211    @Override
2212    protected void onDraw(Canvas canvas) {
2213        super.onDraw(canvas);
2214
2215        // Draw the margin drawable between pages if needed.
2216        if (mPageMargin > 0 && mMarginDrawable != null && mItems.size() > 0 && mAdapter != null) {
2217            final int scrollX = getScrollX();
2218            final int width = getWidth();
2219
2220            final float marginOffset = (float) mPageMargin / width;
2221            int itemIndex = 0;
2222            ItemInfo ii = mItems.get(0);
2223            float offset = ii.offset;
2224            final int itemCount = mItems.size();
2225            final int firstPos = ii.position;
2226            final int lastPos = mItems.get(itemCount - 1).position;
2227            for (int pos = firstPos; pos < lastPos; pos++) {
2228                while (pos > ii.position && itemIndex < itemCount) {
2229                    ii = mItems.get(++itemIndex);
2230                }
2231
2232                float drawAt;
2233                if (pos == ii.position) {
2234                    drawAt = (ii.offset + ii.widthFactor) * width;
2235                    offset = ii.offset + ii.widthFactor + marginOffset;
2236                } else {
2237                    float widthFactor = mAdapter.getPageWidth(pos);
2238                    drawAt = (offset + widthFactor) * width;
2239                    offset += widthFactor + marginOffset;
2240                }
2241
2242                if (drawAt + mPageMargin > scrollX) {
2243                    mMarginDrawable.setBounds((int) drawAt, mTopPageBounds,
2244                            (int) (drawAt + mPageMargin + 0.5f), mBottomPageBounds);
2245                    mMarginDrawable.draw(canvas);
2246                }
2247
2248                if (drawAt > scrollX + width) {
2249                    break; // No more visible, no sense in continuing
2250                }
2251            }
2252        }
2253    }
2254
2255    /**
2256     * Start a fake drag of the pager.
2257     *
2258     * <p>A fake drag can be useful if you want to synchronize the motion of the ViewPager
2259     * with the touch scrolling of another view, while still letting the ViewPager
2260     * control the snapping motion and fling behavior. (e.g. parallax-scrolling tabs.)
2261     * Call {@link #fakeDragBy(float)} to simulate the actual drag motion. Call
2262     * {@link #endFakeDrag()} to complete the fake drag and fling as necessary.
2263     *
2264     * <p>During a fake drag the ViewPager will ignore all touch events. If a real drag
2265     * is already in progress, this method will return false.
2266     *
2267     * @return true if the fake drag began successfully, false if it could not be started.
2268     *
2269     * @see #fakeDragBy(float)
2270     * @see #endFakeDrag()
2271     */
2272    public boolean beginFakeDrag() {
2273        if (mIsBeingDragged) {
2274            return false;
2275        }
2276        mFakeDragging = true;
2277        setScrollState(SCROLL_STATE_DRAGGING);
2278        mInitialMotionX = mLastMotionX = 0;
2279        if (mVelocityTracker == null) {
2280            mVelocityTracker = VelocityTracker.obtain();
2281        } else {
2282            mVelocityTracker.clear();
2283        }
2284        final long time = SystemClock.uptimeMillis();
2285        final MotionEvent ev = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
2286        mVelocityTracker.addMovement(ev);
2287        ev.recycle();
2288        mFakeDragBeginTime = time;
2289        return true;
2290    }
2291
2292    /**
2293     * End a fake drag of the pager.
2294     *
2295     * @see #beginFakeDrag()
2296     * @see #fakeDragBy(float)
2297     */
2298    public void endFakeDrag() {
2299        if (!mFakeDragging) {
2300            throw new IllegalStateException("No fake drag in progress. Call beginFakeDrag first.");
2301        }
2302
2303        final VelocityTracker velocityTracker = mVelocityTracker;
2304        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
2305        int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(
2306                velocityTracker, mActivePointerId);
2307        mPopulatePending = true;
2308        final int width = getClientWidth();
2309        final int scrollX = getScrollX();
2310        final ItemInfo ii = infoForCurrentScrollPosition();
2311        final int currentPage = ii.position;
2312        final float pageOffset = (((float) scrollX / width) - ii.offset) / ii.widthFactor;
2313        final int totalDelta = (int) (mLastMotionX - mInitialMotionX);
2314        int nextPage = determineTargetPage(currentPage, pageOffset, initialVelocity,
2315                totalDelta);
2316        setCurrentItemInternal(nextPage, true, true, initialVelocity);
2317        endDrag();
2318
2319        mFakeDragging = false;
2320    }
2321
2322    /**
2323     * Fake drag by an offset in pixels. You must have called {@link #beginFakeDrag()} first.
2324     *
2325     * @param xOffset Offset in pixels to drag by.
2326     * @see #beginFakeDrag()
2327     * @see #endFakeDrag()
2328     */
2329    public void fakeDragBy(float xOffset) {
2330        if (!mFakeDragging) {
2331            throw new IllegalStateException("No fake drag in progress. Call beginFakeDrag first.");
2332        }
2333
2334        mLastMotionX += xOffset;
2335
2336        float oldScrollX = getScrollX();
2337        float scrollX = oldScrollX - xOffset;
2338        final int width = getClientWidth();
2339
2340        float leftBound = width * mFirstOffset;
2341        float rightBound = width * mLastOffset;
2342
2343        final ItemInfo firstItem = mItems.get(0);
2344        final ItemInfo lastItem = mItems.get(mItems.size() - 1);
2345        if (firstItem.position != 0) {
2346            leftBound = firstItem.offset * width;
2347        }
2348        if (lastItem.position != mAdapter.getCount() - 1) {
2349            rightBound = lastItem.offset * width;
2350        }
2351
2352        if (scrollX < leftBound) {
2353            scrollX = leftBound;
2354        } else if (scrollX > rightBound) {
2355            scrollX = rightBound;
2356        }
2357        // Don't lose the rounded component
2358        mLastMotionX += scrollX - (int) scrollX;
2359        scrollTo((int) scrollX, getScrollY());
2360        pageScrolled((int) scrollX);
2361
2362        // Synthesize an event for the VelocityTracker.
2363        final long time = SystemClock.uptimeMillis();
2364        final MotionEvent ev = MotionEvent.obtain(mFakeDragBeginTime, time, MotionEvent.ACTION_MOVE,
2365                mLastMotionX, 0, 0);
2366        mVelocityTracker.addMovement(ev);
2367        ev.recycle();
2368    }
2369
2370    /**
2371     * Returns true if a fake drag is in progress.
2372     *
2373     * @return true if currently in a fake drag, false otherwise.
2374     *
2375     * @see #beginFakeDrag()
2376     * @see #fakeDragBy(float)
2377     * @see #endFakeDrag()
2378     */
2379    public boolean isFakeDragging() {
2380        return mFakeDragging;
2381    }
2382
2383    private void onSecondaryPointerUp(MotionEvent ev) {
2384        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
2385        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
2386        if (pointerId == mActivePointerId) {
2387            // This was our active pointer going up. Choose a new
2388            // active pointer and adjust accordingly.
2389            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
2390            mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
2391            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
2392            if (mVelocityTracker != null) {
2393                mVelocityTracker.clear();
2394            }
2395        }
2396    }
2397
2398    private void endDrag() {
2399        mIsBeingDragged = false;
2400        mIsUnableToDrag = false;
2401
2402        if (mVelocityTracker != null) {
2403            mVelocityTracker.recycle();
2404            mVelocityTracker = null;
2405        }
2406    }
2407
2408    private void setScrollingCacheEnabled(boolean enabled) {
2409        if (mScrollingCacheEnabled != enabled) {
2410            mScrollingCacheEnabled = enabled;
2411            if (USE_CACHE) {
2412                final int size = getChildCount();
2413                for (int i = 0; i < size; ++i) {
2414                    final View child = getChildAt(i);
2415                    if (child.getVisibility() != GONE) {
2416                        child.setDrawingCacheEnabled(enabled);
2417                    }
2418                }
2419            }
2420        }
2421    }
2422
2423    public boolean canScrollHorizontally(int direction) {
2424        if (mAdapter == null) {
2425            return false;
2426        }
2427
2428        final int width = getClientWidth();
2429        final int scrollX = getScrollX();
2430        if (direction < 0) {
2431            return (scrollX > (int) (width * mFirstOffset));
2432        } else if (direction > 0) {
2433            return (scrollX < (int) (width * mLastOffset));
2434        } else {
2435            return false;
2436        }
2437    }
2438
2439    /**
2440     * Tests scrollability within child views of v given a delta of dx.
2441     *
2442     * @param v View to test for horizontal scrollability
2443     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
2444     *               or just its children (false).
2445     * @param dx Delta scrolled in pixels
2446     * @param x X coordinate of the active touch point
2447     * @param y Y coordinate of the active touch point
2448     * @return true if child views of v can be scrolled by delta of dx.
2449     */
2450    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
2451        if (v instanceof ViewGroup) {
2452            final ViewGroup group = (ViewGroup) v;
2453            final int scrollX = v.getScrollX();
2454            final int scrollY = v.getScrollY();
2455            final int count = group.getChildCount();
2456            // Count backwards - let topmost views consume scroll distance first.
2457            for (int i = count - 1; i >= 0; i--) {
2458                // TODO: Add versioned support here for transformed views.
2459                // This will not work for transformed views in Honeycomb+
2460                final View child = group.getChildAt(i);
2461                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
2462                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
2463                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
2464                                y + scrollY - child.getTop())) {
2465                    return true;
2466                }
2467            }
2468        }
2469
2470        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
2471    }
2472
2473    @Override
2474    public boolean dispatchKeyEvent(KeyEvent event) {
2475        // Let the focused view and/or our descendants get the key first
2476        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
2477    }
2478
2479    /**
2480     * You can call this function yourself to have the scroll view perform
2481     * scrolling from a key event, just as if the event had been dispatched to
2482     * it by the view hierarchy.
2483     *
2484     * @param event The key event to execute.
2485     * @return Return true if the event was handled, else false.
2486     */
2487    public boolean executeKeyEvent(KeyEvent event) {
2488        boolean handled = false;
2489        if (event.getAction() == KeyEvent.ACTION_DOWN) {
2490            switch (event.getKeyCode()) {
2491                case KeyEvent.KEYCODE_DPAD_LEFT:
2492                    handled = arrowScroll(FOCUS_LEFT);
2493                    break;
2494                case KeyEvent.KEYCODE_DPAD_RIGHT:
2495                    handled = arrowScroll(FOCUS_RIGHT);
2496                    break;
2497                case KeyEvent.KEYCODE_TAB:
2498                    if (Build.VERSION.SDK_INT >= 11) {
2499                        // The focus finder had a bug handling FOCUS_FORWARD and FOCUS_BACKWARD
2500                        // before Android 3.0. Ignore the tab key on those devices.
2501                        if (KeyEventCompat.hasNoModifiers(event)) {
2502                            handled = arrowScroll(FOCUS_FORWARD);
2503                        } else if (KeyEventCompat.hasModifiers(event, KeyEvent.META_SHIFT_ON)) {
2504                            handled = arrowScroll(FOCUS_BACKWARD);
2505                        }
2506                    }
2507                    break;
2508            }
2509        }
2510        return handled;
2511    }
2512
2513    public boolean arrowScroll(int direction) {
2514        View currentFocused = findFocus();
2515        if (currentFocused == this) {
2516            currentFocused = null;
2517        } else if (currentFocused != null) {
2518            boolean isChild = false;
2519            for (ViewParent parent = currentFocused.getParent(); parent instanceof ViewGroup;
2520                    parent = parent.getParent()) {
2521                if (parent == this) {
2522                    isChild = true;
2523                    break;
2524                }
2525            }
2526            if (!isChild) {
2527                // This would cause the focus search down below to fail in fun ways.
2528                final StringBuilder sb = new StringBuilder();
2529                sb.append(currentFocused.getClass().getSimpleName());
2530                for (ViewParent parent = currentFocused.getParent(); parent instanceof ViewGroup;
2531                        parent = parent.getParent()) {
2532                    sb.append(" => ").append(parent.getClass().getSimpleName());
2533                }
2534                Log.e(TAG, "arrowScroll tried to find focus based on non-child " +
2535                        "current focused view " + sb.toString());
2536                currentFocused = null;
2537            }
2538        }
2539
2540        boolean handled = false;
2541
2542        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused,
2543                direction);
2544        if (nextFocused != null && nextFocused != currentFocused) {
2545            if (direction == View.FOCUS_LEFT) {
2546                // If there is nothing to the left, or this is causing us to
2547                // jump to the right, then what we really want to do is page left.
2548                final int nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left;
2549                final int currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left;
2550                if (currentFocused != null && nextLeft >= currLeft) {
2551                    handled = pageLeft();
2552                } else {
2553                    handled = nextFocused.requestFocus();
2554                }
2555            } else if (direction == View.FOCUS_RIGHT) {
2556                // If there is nothing to the right, or this is causing us to
2557                // jump to the left, then what we really want to do is page right.
2558                final int nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left;
2559                final int currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left;
2560                if (currentFocused != null && nextLeft <= currLeft) {
2561                    handled = pageRight();
2562                } else {
2563                    handled = nextFocused.requestFocus();
2564                }
2565            }
2566        } else if (direction == FOCUS_LEFT || direction == FOCUS_BACKWARD) {
2567            // Trying to move left and nothing there; try to page.
2568            handled = pageLeft();
2569        } else if (direction == FOCUS_RIGHT || direction == FOCUS_FORWARD) {
2570            // Trying to move right and nothing there; try to page.
2571            handled = pageRight();
2572        }
2573        if (handled) {
2574            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
2575        }
2576        return handled;
2577    }
2578
2579    private Rect getChildRectInPagerCoordinates(Rect outRect, View child) {
2580        if (outRect == null) {
2581            outRect = new Rect();
2582        }
2583        if (child == null) {
2584            outRect.set(0, 0, 0, 0);
2585            return outRect;
2586        }
2587        outRect.left = child.getLeft();
2588        outRect.right = child.getRight();
2589        outRect.top = child.getTop();
2590        outRect.bottom = child.getBottom();
2591
2592        ViewParent parent = child.getParent();
2593        while (parent instanceof ViewGroup && parent != this) {
2594            final ViewGroup group = (ViewGroup) parent;
2595            outRect.left += group.getLeft();
2596            outRect.right += group.getRight();
2597            outRect.top += group.getTop();
2598            outRect.bottom += group.getBottom();
2599
2600            parent = group.getParent();
2601        }
2602        return outRect;
2603    }
2604
2605    boolean pageLeft() {
2606        if (mCurItem > 0) {
2607            setCurrentItem(mCurItem-1, true);
2608            return true;
2609        }
2610        return false;
2611    }
2612
2613    boolean pageRight() {
2614        if (mAdapter != null && mCurItem < (mAdapter.getCount()-1)) {
2615            setCurrentItem(mCurItem+1, true);
2616            return true;
2617        }
2618        return false;
2619    }
2620
2621    /**
2622     * We only want the current page that is being shown to be focusable.
2623     */
2624    @Override
2625    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
2626        final int focusableCount = views.size();
2627
2628        final int descendantFocusability = getDescendantFocusability();
2629
2630        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
2631            for (int i = 0; i < getChildCount(); i++) {
2632                final View child = getChildAt(i);
2633                if (child.getVisibility() == VISIBLE) {
2634                    ItemInfo ii = infoForChild(child);
2635                    if (ii != null && ii.position == mCurItem) {
2636                        child.addFocusables(views, direction, focusableMode);
2637                    }
2638                }
2639            }
2640        }
2641
2642        // we add ourselves (if focusable) in all cases except for when we are
2643        // FOCUS_AFTER_DESCENDANTS and there are some descendants focusable.  this is
2644        // to avoid the focus search finding layouts when a more precise search
2645        // among the focusable children would be more interesting.
2646        if (
2647            descendantFocusability != FOCUS_AFTER_DESCENDANTS ||
2648                // No focusable descendants
2649                (focusableCount == views.size())) {
2650            // Note that we can't call the superclass here, because it will
2651            // add all views in.  So we need to do the same thing View does.
2652            if (!isFocusable()) {
2653                return;
2654            }
2655            if ((focusableMode & FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE &&
2656                    isInTouchMode() && !isFocusableInTouchMode()) {
2657                return;
2658            }
2659            if (views != null) {
2660                views.add(this);
2661            }
2662        }
2663    }
2664
2665    /**
2666     * We only want the current page that is being shown to be touchable.
2667     */
2668    @Override
2669    public void addTouchables(ArrayList<View> views) {
2670        // Note that we don't call super.addTouchables(), which means that
2671        // we don't call View.addTouchables().  This is okay because a ViewPager
2672        // is itself not touchable.
2673        for (int i = 0; i < getChildCount(); i++) {
2674            final View child = getChildAt(i);
2675            if (child.getVisibility() == VISIBLE) {
2676                ItemInfo ii = infoForChild(child);
2677                if (ii != null && ii.position == mCurItem) {
2678                    child.addTouchables(views);
2679                }
2680            }
2681        }
2682    }
2683
2684    /**
2685     * We only want the current page that is being shown to be focusable.
2686     */
2687    @Override
2688    protected boolean onRequestFocusInDescendants(int direction,
2689            Rect previouslyFocusedRect) {
2690        int index;
2691        int increment;
2692        int end;
2693        int count = getChildCount();
2694        if ((direction & FOCUS_FORWARD) != 0) {
2695            index = 0;
2696            increment = 1;
2697            end = count;
2698        } else {
2699            index = count - 1;
2700            increment = -1;
2701            end = -1;
2702        }
2703        for (int i = index; i != end; i += increment) {
2704            View child = getChildAt(i);
2705            if (child.getVisibility() == VISIBLE) {
2706                ItemInfo ii = infoForChild(child);
2707                if (ii != null && ii.position == mCurItem) {
2708                    if (child.requestFocus(direction, previouslyFocusedRect)) {
2709                        return true;
2710                    }
2711                }
2712            }
2713        }
2714        return false;
2715    }
2716
2717    @Override
2718    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
2719        // Dispatch scroll events from this ViewPager.
2720        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_SCROLLED) {
2721            return super.dispatchPopulateAccessibilityEvent(event);
2722        }
2723
2724        // Dispatch all other accessibility events from the current page.
2725        final int childCount = getChildCount();
2726        for (int i = 0; i < childCount; i++) {
2727            final View child = getChildAt(i);
2728            if (child.getVisibility() == VISIBLE) {
2729                final ItemInfo ii = infoForChild(child);
2730                if (ii != null && ii.position == mCurItem &&
2731                        child.dispatchPopulateAccessibilityEvent(event)) {
2732                    return true;
2733                }
2734            }
2735        }
2736
2737        return false;
2738    }
2739
2740    @Override
2741    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
2742        return new LayoutParams();
2743    }
2744
2745    @Override
2746    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
2747        return generateDefaultLayoutParams();
2748    }
2749
2750    @Override
2751    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
2752        return p instanceof LayoutParams && super.checkLayoutParams(p);
2753    }
2754
2755    @Override
2756    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
2757        return new LayoutParams(getContext(), attrs);
2758    }
2759
2760    class MyAccessibilityDelegate extends AccessibilityDelegateCompat {
2761
2762        @Override
2763        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
2764            super.onInitializeAccessibilityEvent(host, event);
2765            event.setClassName(ViewPager.class.getName());
2766            final AccessibilityRecordCompat recordCompat = AccessibilityRecordCompat.obtain();
2767            recordCompat.setScrollable(canScroll());
2768            if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_SCROLLED
2769                    && mAdapter != null) {
2770                recordCompat.setItemCount(mAdapter.getCount());
2771                recordCompat.setFromIndex(mCurItem);
2772                recordCompat.setToIndex(mCurItem);
2773            }
2774        }
2775
2776        @Override
2777        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
2778            super.onInitializeAccessibilityNodeInfo(host, info);
2779            info.setClassName(ViewPager.class.getName());
2780            info.setScrollable(canScroll());
2781            if (canScrollHorizontally(1)) {
2782                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
2783            }
2784            if (canScrollHorizontally(-1)) {
2785                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
2786            }
2787        }
2788
2789        @Override
2790        public boolean performAccessibilityAction(View host, int action, Bundle args) {
2791            if (super.performAccessibilityAction(host, action, args)) {
2792                return true;
2793            }
2794            switch (action) {
2795                case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD: {
2796                    if (canScrollHorizontally(1)) {
2797                        setCurrentItem(mCurItem + 1);
2798                        return true;
2799                    }
2800                } return false;
2801                case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD: {
2802                    if (canScrollHorizontally(-1)) {
2803                        setCurrentItem(mCurItem - 1);
2804                        return true;
2805                    }
2806                } return false;
2807            }
2808            return false;
2809        }
2810
2811        private boolean canScroll() {
2812            return (mAdapter != null) && (mAdapter.getCount() > 1);
2813        }
2814    }
2815
2816    private class PagerObserver extends DataSetObserver {
2817        @Override
2818        public void onChanged() {
2819            dataSetChanged();
2820        }
2821        @Override
2822        public void onInvalidated() {
2823            dataSetChanged();
2824        }
2825    }
2826
2827    /**
2828     * Layout parameters that should be supplied for views added to a
2829     * ViewPager.
2830     */
2831    public static class LayoutParams extends ViewGroup.LayoutParams {
2832        /**
2833         * true if this view is a decoration on the pager itself and not
2834         * a view supplied by the adapter.
2835         */
2836        public boolean isDecor;
2837
2838        /**
2839         * Gravity setting for use on decor views only:
2840         * Where to position the view page within the overall ViewPager
2841         * container; constants are defined in {@link android.view.Gravity}.
2842         */
2843        public int gravity;
2844
2845        /**
2846         * Width as a 0-1 multiplier of the measured pager width
2847         */
2848        float widthFactor = 0.f;
2849
2850        /**
2851         * true if this view was added during layout and needs to be measured
2852         * before being positioned.
2853         */
2854        boolean needsMeasure;
2855
2856        /**
2857         * Adapter position this view is for if !isDecor
2858         */
2859        int position;
2860
2861        /**
2862         * Current child index within the ViewPager that this view occupies
2863         */
2864        int childIndex;
2865
2866        public LayoutParams() {
2867            super(FILL_PARENT, FILL_PARENT);
2868        }
2869
2870        public LayoutParams(Context context, AttributeSet attrs) {
2871            super(context, attrs);
2872
2873            final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
2874            gravity = a.getInteger(0, Gravity.TOP);
2875            a.recycle();
2876        }
2877    }
2878
2879    static class ViewPositionComparator implements Comparator<View> {
2880        @Override
2881        public int compare(View lhs, View rhs) {
2882            final LayoutParams llp = (LayoutParams) lhs.getLayoutParams();
2883            final LayoutParams rlp = (LayoutParams) rhs.getLayoutParams();
2884            if (llp.isDecor != rlp.isDecor) {
2885                return llp.isDecor ? 1 : -1;
2886            }
2887            return llp.position - rlp.position;
2888        }
2889    }
2890}
2891
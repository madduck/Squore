/*
 * Copyright (C) 2017  Iddo Hoeve
 *
 * Squore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.doubleyellow.scoreboard.cast.framework;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.Menu;

import com.doubleyellow.android.util.ColorUtil;
import com.doubleyellow.scoreboard.Brand;
import com.doubleyellow.scoreboard.history.MatchGameScoresView;
import com.doubleyellow.scoreboard.R;
import com.doubleyellow.scoreboard.main.ScoreBoard;
import com.doubleyellow.scoreboard.model.Model;
import com.doubleyellow.scoreboard.model.Player;
import com.doubleyellow.scoreboard.prefs.ColorPrefs;
import com.doubleyellow.scoreboard.timer.TimerView;

import com.doubleyellow.scoreboard.vico.IBoard;
import com.doubleyellow.util.ListUtil;
import com.doubleyellow.util.MapUtil;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Takes care of sending JSON messages to the 'webpage' loaded by the chromecast device.
 * - Setting names of players and optionally flags
 * - Setting score
 * - Setting serve side button text
 * - Starting timers (actual countdown implemented on web page itself in javascript)
 *
 * chrome://inspect
 */
public class CastHelper implements com.doubleyellow.scoreboard.cast.ICastHelper
{
    private static final String TAG = "SB." + CastHelper.class.getSimpleName();

    private ScoreBoard  m_activity   = null;
    private CastContext castContext  = null; /* has nothing to do with android.content.Context */
    private CastSession castSession  = null;
    private String      sPackageName = null; /* serves as Namespace */

    @Override public void initCasting(ScoreBoard activity) {
        m_activity = activity;
        if ( castContext == null ) {
            try {
                castContext = CastContext.getSharedInstance(activity); // requires com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME to be specified in Manifest.xml
            } catch (Exception e) {
                Log.w(TAG, "No casting ..." + e.getMessage());
                //e.printStackTrace(); // com.google.android.gms.dynamite.DynamiteModule$LoadingException: No acceptable module found. Local version is 0 and remote version is 0 (Samsung S4 with custom ROM 8.1)
            }
        }
        sPackageName = m_activity.getPackageName();

        checkPlayServices();
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(m_activity);
        if ( result != ConnectionResult.SUCCESS ) {
            //Google Play Services app is not available or version is not up to date. Error the error condition here
            return false;
        }
        return true;
    }
    @Override public void initCastMenu(Activity activity, Menu menu, int iResIdMenuItem) {
        CastButtonFactory.setUpMediaRouteButton(activity, menu, iResIdMenuItem); // TODO: set to visible always in new cast framework?
    }

    @Override public void onActivityStart_Cast() {

    }
    @Override public void onActivityStop_Cast() {
        //cleanup();
    }
    @Override public void onActivityPause_Cast() {
        if ( castContext == null ) { return; }
        SessionManager sessionManager = castContext.getSessionManager();
        sessionManager.removeSessionManagerListener(sessionManagerListener, CastSession.class);
    }
    @Override public void onActivityResume_Cast() {
        if ( castContext == null ) { return; }
        SessionManager sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener, CastSession.class);

        // e.g. after screen rotation
        if ( (castSession == null) && (castContext != null) ) {
            // Get the current session if there is one
            castSession = sessionManager.getCurrentCastSession();

            updateViewWithColorAndScore(m_activity, m_matchModel);
        }

        castContext.addCastStateListener(new CastStateListener() {
            @Override public void onCastStateChanged(int i) {
                Log.d(TAG, "onCastStateChanged: " + i); // seen 2 (stopped) and 3 (connecting) and 4 (connected)
            }
        });
    }


    @Override public boolean isCasting() {
        return castSession != null;
    }

    private Model m_matchModel = null;
    @Override public void setModelForCast(Model matchModel) {
      //if ( isCasting() == false ) { return; }
      //Log.d(TAG, "New model for cast passed in : " + matchModel);
        if ( matchModel != m_matchModel && (matchModel != null)) {
            m_matchModel = matchModel;
        }
        updateViewWithColorAndScore(m_activity, m_matchModel);
    }

    private void sendMessage(Integer iBoardResId, Object oValue) {
        sendMessage(iBoardResId, oValue, "text");
    }
    // sProperty = background-color
    public void sendMessage(Integer iBoardResId, Object oValue, String sProperty) {
        if ( isCasting() == false ) { return; }
        if ( iBoardResId == null ) {
            return;
        }
        Resources resources = m_activity.getResources();
        try {
            String sResName = resources.getResourceName(iBoardResId);
                   sResName = sResName.replaceFirst(".*/", "");

            sendMessage(sResName, oValue, sProperty);
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String sResName, Object oValue, String sProperty) {
        if ( isCasting() == false ) { return; }
        if ( sProperty.contains("color") && oValue instanceof Integer ) {
            oValue = ColorUtil.getRGBString((Integer) oValue);
        }
        Map map = MapUtil.getMap("id", sResName, "property", sProperty, "value", oValue);
        sendMapAsJsonMessage(map);
    }

    public void sendFunction(String sFunction) {
        if ( isCasting() == false ) { return; }
        Map map = MapUtil.getMap("func", sFunction);
        sendMapAsJsonMessage(map);
    }

    private void sendMapAsJsonMessage(Map map) {
        JSONObject jsonObject = new JSONObject(map);
        String sMsg = jsonObject.toString();
        sendJsonMessage(sMsg);
    }

    private void sendJsonMessage(String sMsg) {
        Log.d(TAG, "sendMessage: " + sMsg);
        try {
            castSession.sendMessage("urn:x-cast:" + sPackageName, sMsg);
        } catch (Exception e) {
            // seen IllegalStateException crashing the app reported in PlayStore
            e.printStackTrace();
        }
    }

    @Override public TimerView getTimerView() {
        // return a timerview that just sends messages at certain times to update Cast screen
        return new TimerView() {
            private int iStartAt = 0;
            private boolean bIsShowing = false;
            @Override public void setTitle(String s) { }
            @Override public void setTime(String s) { /* not used. Cast will do it's own countdown */ }
            @Override public void setTime(int iStartedCountDownAtSecs, int iSecsLeft, int iReminderAtSecs) {
                if ( (bIsShowing == false)
                   || ( Math.abs(this.iStartAt - iSecsLeft) > 5 && (iSecsLeft > 0)) ) {
                    this.iStartAt = iSecsLeft;
                    show();
                }
            }
            @Override public void setWarnMessage(String s) { }
            @Override public void setPausedMessage(String s) { }
            @Override public void cancel() {
                sendFunction("CountDownTimer.cancel()");
                bIsShowing = false;
            }
            @Override public void timeIsUp() {
                String sTime = m_activity.getString(R.string.oa_time);
                sendMessage(R.id.btn_timer, sTime);
            }
            @Override public void show() {
                if ( iStartAt > 0 ) {
                    sendFunction("CountDownTimer.show(" + this.iStartAt + ")");
                    bIsShowing = true;
                }
            }
            @Override public boolean isShowing() { return bIsShowing; }
        };
    }

    @Override public void castColors(Map<ColorPrefs.ColorTarget, Integer> mColors) {

    }

    @Override public void castDurationChronos() {

    }

    @Override public void castGamesWonAppearance() {
        // already taken care of by updateViewWithColorAndScore()
    }

    private void cleanup() {
        castSession = null;
/*
        if ( m_matchModel != null ) {
            int iCleared = m_matchModel.clearListeners(".*.cast.framework.*");
            m_matchModel = null;
        }
*/
    }

    private  IBoard      iBoard   = null;
    public void setIBoard(IBoard iBoard) {
        this.iBoard = iBoard;
        updateViewWithColorAndScore(m_activity, m_matchModel);
    }

    /** Listener is to get hold of castSession. Cast session is used for sending messages */
    private SessionManagerListener sessionManagerListener = new SessionManagerListener() {
        @Override public void onSessionStarted(Session session, String s) {
            Log.d(TAG, "Cast session started: " + s);
            castSession = (CastSession) session;

            updateViewWithColorAndScore(m_activity, m_matchModel);
        }

        @Override public void onSessionResumed(Session session, boolean b) {
            Log.d(TAG, "Cast session resumed " + b);
            castSession = (CastSession) session;

            updateViewWithColorAndScore(m_activity, m_matchModel);
        }

        @Override public void onSessionSuspended(Session session, int i) { }
        @Override public void onSessionStarting(Session session) {
            Log.d(TAG, "Cast session starting...");
        }
        @Override public void onSessionResuming(Session session, String s) { }
        @Override public void onSessionEnding(Session session) { }
        @Override public void onSessionEnded(Session session, int i) {
            cleanup();
        }
        @Override public void onSessionStartFailed(Session session, int i) {
            Log.w(TAG, "Cast session failed to start: " + i);
        }
        @Override public void onSessionResumeFailed(Session session, int i) {
            Log.w(TAG, "Cast session failed to resume: " + i);
        }
    };

    private void updateViewWithColorAndScore(Context context, Model matchModel) {
        if ( isCasting() == false || iBoard == null || matchModel == null ) {
          //Log.w(TAG, "Not updating (isCasting=" + isCasting() + ", iBoard=" + iBoard + ", model=" + matchModel + ")");
            return;
        }
        //castSession.addCastListener(new CastListener());
        try {
            castSession.setMessageReceivedCallbacks("urn:x-cast:" + sPackageName, new Cast.MessageReceivedCallback() {
                @Override public void onMessageReceived(CastDevice castDevice, String sNamespace, String sMsg) {
                    Log.d(TAG, String.format("received message back from %s [%s] : %s", castDevice.getFriendlyName(), sNamespace, sMsg));

                    try {
                        JSONObject mMessage = new JSONObject(sMsg);
                        m_activity.handleMessageFromCast(mMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }


        CastDevice castDevice = castSession.getCastDevice();
        String sFriendlyName = castDevice.getFriendlyName();
        Log.d(TAG, "castDevice.getFriendlyName(): " + sFriendlyName); // e.g. Living
        Cast.ApplicationConnectionResult applicationConnectionResult = castSession.getApplicationConnectionResult();

        ApplicationMetadata applicationMetadata = castSession.getApplicationMetadata();
        List<String> supportedNamespaces = applicationMetadata.getSupportedNamespaces(); // the namespaces added by the receiver using m_crContext.addCustomMessageListener(namespace)
        Log.d(TAG, "Supported namespaces: " + ListUtil.join(supportedNamespaces, "\n") );
        // [ urn:x-cast:com.google.cast.cac
        // , urn:x-cast:com.google.cast.debugoverlay
        // , urn:x-cast:com.google.cast.debuglogger
        // , urn:x-cast:com.doubleyellow.scoreboard
        // , urn:x-cast:com.doubleyellow.badminton
        // , urn:x-cast:com.doubleyellow.tennispadel
        // , urn:x-cast:com.doubleyellow.tabletennis
        // , urn:x-cast:com.google.cast.broadcast
        // , urn:x-cast:com.google.cast.media
        // ]

        RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        Log.d(TAG, "remoteMediaClient.getNamespace(): " + remoteMediaClient.getNamespace()); // urn:x-cast:com.google.cast.media
/*
                remoteMediaClient.registerCallback(new RemoteMediaClient.Callback() {
                });
*/

        //GoogleCast.EventEmitter.addListener();
        // W CDC|API|500: [API] Ignoring message. Namespace 'urn:x-cast:com.doubleyellow.scoreboard' has not been registered.

        Log.d(TAG, "Updating cast (CAF)");

        Map<ColorPrefs.ColorTarget, Integer> mColors = ColorPrefs.getTarget2colorMapping(context);
        iBoard.initColors(mColors);

/*
        if ( bShowGraphDuringTimer ) {
            iBoard.getTimerView().cancel();
        }
*/
        iBoard.updateGameScores();
        iBoard.updateGameBallMessage();
        if ( Brand.isGameSetMatch() ) {
            MatchGameScoresView.ScoresToShow scoresToShow = iBoard.updateSetScoresToShow(false);
            sendFunction("GameScores.display(" + scoresToShow.equals(MatchGameScoresView.ScoresToShow.GamesWonPerSet) + ")");
        }

        for(Player p: Model.getPlayers()) {
            iBoard.updateScore(p, matchModel.getScore(p));
            iBoard.updateServeSide    (p, matchModel.getNextDoubleServe(p), matchModel.getNextServeSide(p), matchModel.isLastPointHandout());
            iBoard.updatePlayerAvatar (p, matchModel.getAvatar (p));
            iBoard.updatePlayerCountry(p, matchModel.getCountry(p));
            iBoard.updatePlayerClub   (p, matchModel.getClub   (p));
            iBoard.updatePlayerName   (p, matchModel.getName   (p), matchModel.isDoubles()); // player name last... if both are communicate cast screen will display screen elements
        }
    }

    /** Not invoked very consistently... it seems */
/*
    private static class CastListener extends Cast.Listener
    {
        private CastListener() {
            super();
        }

        @Override public void onApplicationStatusChanged() {
            Log.d(TAG, "onApplicationStatusChanged");
            super.onApplicationStatusChanged();
        }

        @Override public void onApplicationMetadataChanged(ApplicationMetadata applicationMetadata) {
            Log.d(TAG, "onApplicationMetadataChanged(" + applicationMetadata + ")");
            super.onApplicationMetadataChanged(applicationMetadata); // e.g. holds "My Test Receiver" and value of CUSTOM_RECEIVER_APP_ID_brand_test
        }

        @Override public void onApplicationDisconnected(int i) {
            Log.d(TAG, "onApplicationDisconnected(" + i + ")");
            super.onApplicationDisconnected(i);
        }

        @Override public void onActiveInputStateChanged(int i) {
            Log.d(TAG, "onActiveInputStateChanged(" + i + ")");
            super.onActiveInputStateChanged(i); // seen: -1
        }

        @Override public void onStandbyStateChanged(int i) {
            Log.d(TAG, "onStandbyStateChanged(" + i + ")");
            super.onStandbyStateChanged(i); // seen -1
        }

        @Override public void onVolumeChanged() {
            Log.d(TAG, "onVolumeChanged");
            super.onVolumeChanged();
        }
    }
*/
}

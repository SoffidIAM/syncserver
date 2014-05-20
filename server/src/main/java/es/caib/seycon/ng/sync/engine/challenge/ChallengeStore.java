package es.caib.seycon.ng.sync.engine.challenge;

import java.sql.Timestamp;
import java.util.Hashtable;
import java.util.Random;

import es.caib.seycon.ng.comu.Challenge;

public class ChallengeStore {
    private static final int MAX_SECRET_CHAR = 62;
    private static ChallengeStore theChallengeStore;
    private int nextChallenge = 1;
    private Hashtable<String, Challenge> challenges = new Hashtable<String, Challenge>();

    public static ChallengeStore getInstance() {
        if (theChallengeStore == null)
            theChallengeStore = new ChallengeStore();
        return theChallengeStore;
    }
    
    public Challenge newChallenge (int type)
    {
        String id ;
        do {
            if (type == Challenge.TYPE_KERBEROS || type == Challenge.TYPE_CERT|| type == Challenge.TYPE_PASSWORD)
            {
                id = generateSessionKey();
            } else {
                id = Integer.toString(nextChallenge ++);
                if (nextChallenge > 10000)
                    nextChallenge = 1;
            }
        } while (getChallenge (id) != null);
       
        Challenge ch = new Challenge ();
        ch.setChallengeId(id);
        ch.setTimeStamp( new Timestamp (System.currentTimeMillis()));
        ch.setType(type);
        challenges.put(id, ch);
        
        return ch;
    }

    public String generateSessionKey() {
        String id;
        Random r = new Random();
        StringBuffer challengeId = new StringBuffer();
        for ( int i = 0; i < 50; i++)
        {
            int n = r.nextInt(MAX_SECRET_CHAR);
            challengeId.append(intToChar(n));
        }
        id = challengeId.toString();
        return id;
    }

    public char intToChar(int n) {
        n = (n % MAX_SECRET_CHAR + MAX_SECRET_CHAR) % MAX_SECRET_CHAR;

        if ( n < 10)
            return (char) ( n + '0');
        else if (n < 36)
            return (char) ( n + 'a' - 10);
        else
            return (char) ( n + 'A' - 36);
    }

    public int charToInt(char ch) {
        if (ch >= '0' && ch <= '9')
            return (int) ch - '0';
        else if (ch >= 'a' && ch <= 'z')
            return (int) ch - 'a' + 10;
        else
            return (int) ch - 'A' + 36;
    }

    public void removeChallenge (Challenge ch)
    {
        challenges.remove(ch.getChallengeId());
    }
    
    public Challenge getChallenge(String id) {
        long currentTime = System.currentTimeMillis();
        Challenge ch = challenges.get(id);
        if (ch != null && 
            currentTime - ch.getTimeStamp().getTime() > 10 * 60 * 1000) { // 10 minuts
            // es.caib.seycon.ServerApplication.out.println ("Challenge
            // "+ch.id+" timed out");
            challenges.remove(id);
            ch = null;
        }
        return ch;
    }
}

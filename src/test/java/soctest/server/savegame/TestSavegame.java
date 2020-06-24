/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/

package soctest.server.savegame;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCResourceSet;
import soc.game.SOCScenario;
import soc.game.SOCGame.SeatLockState;
import soc.server.SOCGameListAtServer;
import soc.server.SOCServer;
import soc.server.savegame.GameLoaderJSON;
import soc.server.savegame.GameSaverJSON;
import soc.server.savegame.SavedGameModel;
import soc.server.savegame.SavedGameModel.UnsupportedSGMOperationException;
import soc.util.Version;

/**
 * A few tests for {@link GameSaverJSON} and {@link SavedGameModel},
 * using JSON test artifacts under {@code /src/test/resources/resources/savegame}
 * and a junit temporary folder.
 *
 * @see TestLoadgame
 * @since 2.4.00
 */
public class TestSavegame
{
    private static SOCServer srv;

    /** dummy server setup, to avoid IllegalStateException etc from {@link GameLoaderJSON} or {@link GameSaverJSON} */
    @BeforeClass
    public static void setup()
        throws Exception
    {
        srv = new SOCServer("dummy", 0, null, null);
        SavedGameModel.glas = new SOCGameListAtServer(null);
    }

    /** This folder and all contents are created at start of each test method, deleted at end of it */
    @Rule
    public TemporaryFolder testTmpFolder = new TemporaryFolder();

    /** Can't save during initial placement */
    @Test(expected=IllegalStateException.class)
    public void testSaveInitialPlacement()
        throws IOException
    {
        final SOCGame ga = new SOCGame("basic", null);
        ga.addPlayer("p0", 0);
        ga.addPlayer("third", 3);

        ga.startGame();  // create board layout
        assertEquals(SOCGame.START1A, ga.getGameState());
        GameSaverJSON.saveGame(ga, testTmpFolder.getRoot(), "wontsave.game.json", srv);
    }

    /** Saving a game which uses a scenario not yet supported by savegame ({@code SC_PIRI}) should fail. */
    @Test(expected=UnsupportedSGMOperationException.class)
    public void testSaveUnsupportedScenario()
        throws IOException
    {
        final Map<String, SOCGameOption> opts = new HashMap<>();
        SOCGameOption opt = SOCGameOption.getOption("SC", true);
        opt.setStringValue(SOCScenario.K_SC_PIRI);
        opts.put("SC", opt);
        SOCGameOption.adjustOptionsToKnown(opts, null, true);  // add SC's scenario game opts
        final SOCGame ga = new SOCGame("scen", opts);

        UnsupportedSGMOperationException checkResult = null;
        try
        {
            SavedGameModel.checkCanSave(ga);
        } catch (UnsupportedSGMOperationException e) {
            checkResult = e;
        }
        assertNotNull(checkResult);
        assertEquals("admin.savegame.cannot_save.scen", checkResult.getMessage());
        assertEquals("SC_PIRI", checkResult.param1);

        GameSaverJSON.saveGame(ga, testTmpFolder.getRoot(), "wontsave.game.json", srv);
    }

    /** Save a basic game, reload it, check field contents */
    @Test
    public void testBasicSaveLoad()
        throws IOException
    {
        final SOCGame gaSave = new SOCGame("basic", null);
        gaSave.addPlayer("p0", 0);
        gaSave.addPlayer("third", 3);
        assertFalse(gaSave.isSeatVacant(0));
        assertTrue(gaSave.isSeatVacant(1));
        assertTrue(gaSave.isSeatVacant(2));
        assertFalse(gaSave.isSeatVacant(3));
        gaSave.setSeatLock(0, SeatLockState.LOCKED);
        gaSave.setSeatLock(3, SeatLockState.CLEAR_ON_RESET);

        gaSave.startGame();  // create board layout
        assertEquals(SOCGame.START1A, gaSave.getGameState());
        final int firstPN = gaSave.getCurrentPlayerNumber();
        assertEquals(firstPN, gaSave.getFirstPlayer());

        // no pieces placed, but can't save during initial placement
        gaSave.setGameState(SOCGame.ROLL_OR_CARD);
        gaSave.getPlayer(0).getResources().add(new SOCResourceSet(1, 3, 0, 2, 0, 0));

        File saveFile = testTmpFolder.newFile("basic.game.json");
        GameSaverJSON.saveGame(gaSave, testTmpFolder.getRoot(), saveFile.getName(), srv);

        final SavedGameModel sgm = GameLoaderJSON.loadGame(saveFile);
        assertNotNull(sgm);
        assertEquals(SavedGameModel.MODEL_VERSION, sgm.modelVersion);
        assertEquals(Version.versionNumber(), sgm.savedByVersion);
        final SOCGame ga = sgm.getGame();

        assertEquals("basic", ga.getName());
        assertEquals(4, ga.maxPlayers);
        assertEquals(firstPN, ga.getCurrentPlayerNumber());
        assertEquals(firstPN, ga.getFirstPlayer());

        final String[] NAMES = {"p0", null, null, "third"};
        final SeatLockState[] LOCKS =
            {SeatLockState.LOCKED, SeatLockState.UNLOCKED, SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET};
        final int[] TOTAL_VP = {0, 0, 0, 0};
        final int[][] RESOURCES = {{1, 3, 0, 2, 0}, null, null, {0, 0, 0, 0, 0}};
        final int[] PIECES_ALL = {15, 5, 4, 0, 0};
        final int[][] PIECE_COUNTS = {PIECES_ALL, PIECES_ALL, PIECES_ALL, PIECES_ALL};
        TestLoadgame.checkPlayerData(sgm, NAMES, LOCKS, TOTAL_VP, RESOURCES, PIECE_COUNTS, null);
    }

}

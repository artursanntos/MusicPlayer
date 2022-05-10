import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import java.awt.event.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Collections;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice the audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean playerPaused = true;
    private boolean newPlay = false;
    private Song currentSong;
    private int currentFrame = 0;
    private int newFrame;
    private float fullLength;
    private float ms;

    private final Lock lock = new ReentrantLock();

    // Musics string array
    private ArrayList<String[]> Musics = new ArrayList<String[]>();

    private String[][] queue = {};

    private ArrayList<Song> Songs = new ArrayList<Song>();

    // Auxiliar array for shuffle
    private ArrayList<Song> aux = new ArrayList<Song>();

    public Player() {
        /*
        We set all of the buttons and listeners. But some of them were not necessary: MouseClicked,
        MouseDragged, MouseEntered, MouseExited and MouseMoved
         */

        ActionListener buttonListenerPlayNow = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                start();
            }
        };

        ActionListener buttonListenerRemove = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeFromQueue();
            }
        };

        ActionListener buttonListenerAddSong = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addToQueue();
            }
        };

        ActionListener buttonListenerShuffle = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    randomSong();
                } catch (JavaLayerException ex) {
                    ex.printStackTrace();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        };

        ActionListener buttonListenerPrevious = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    previous();
                } catch (JavaLayerException ex) {
                    ex.printStackTrace();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        };

        ActionListener buttonListenerPlayPause = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlayPause();
            }
        };

        ActionListener buttonListenerStop = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stop();
            }
        };

        ActionListener buttonListenerNext = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    next();
                } catch (JavaLayerException ex) {
                    ex.printStackTrace();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        };

        ActionListener buttonListenerRepeat = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(repeat == false){
                    repeat = true;
                }
                else{
                    repeat = false;
                }
            }
        };

        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Pauses the song when the scrubber is pressed
                playerPaused = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // We have to load the song again, because we were having a lot of issues just skipping the frames
                try {
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(currentSong.getBufferedInputStream());
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                } catch (JavaLayerException ex) {
                    ex.printStackTrace();
                }

                int time = window.getScrubberValue();
                float msPerFrame = currentSong.getMsPerFrame();

                int newFrame = (int) (time / msPerFrame);

                window.setTime((int) (time * msPerFrame), (int) currentSong.getMsLength());

                // Trying to skip to frame and resume the song
                try {
                    skipToFrame(newFrame);
                    currentFrame = newFrame;
                    playerPaused = false;
                    playing(currentSong);
                } catch (BitstreamException ex) {
                    ex.printStackTrace();
                }


            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        };

        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {


            }

            @Override
            public void mouseMoved(MouseEvent e) {

            }
        };

        this.window = new PlayerWindow(
                "Sportify", queue, buttonListenerPlayNow, buttonListenerRemove,
                buttonListenerAddSong, buttonListenerShuffle, buttonListenerPrevious,
                buttonListenerPlayPause, buttonListenerStop, buttonListenerNext,
                buttonListenerRepeat, scrubberListenerClick, scrubberListenerMotion
                );

    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?

        // There used to exist a condition here that made Skip to frame only work to jump forward.
        // This was a problem and we removed the coindition
        int framesToSkip = newFrame;
        boolean condition = true;
        while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    /**
     * Adds a song to the Queue and updates Queue displayed.
     *
     * @throws InvalidDataException
     * @throws BitstreamException
     * @throws UnsupportedTagException
     * @throws IOException
     */
    public void addToQueue() {

        try {
            Song newSong = window.getNewSong();

            // Checking if newSong is null because we were having errors with this
            if (newSong != null) {
                Songs.add(newSong);

                String[] songInfo = newSong.getDisplayInfo();

                // Add to Musics array in the form of a string
                Musics.add(songInfo);

                // Update Displayed queue
                getQueueAsArrayAndUpdate();
            }

        }
        catch(InvalidDataException | BitstreamException | UnsupportedTagException | IOException e){
            System.out.println(e);
        }
    }

    /**
     * Removes a song from the queue and updates the queue that the user sees.
     *
     */
    public void removeFromQueue() {
        try {

            // Get music index
            int rmvSong = window.getSelectedIdx();

            // Since it has the same index in both arrays, remove by index in both.
            Musics.remove(rmvSong);
            Songs.remove(rmvSong);
            getQueueAsArrayAndUpdate();

            System.out.println("Musica removida"); // Console

            /*

            More inefficient way of finding the index of the song that will be removed, IT ALSO WORKS!

            for (int i = 0; i < Musics.size(); i++) {
                if( queue[i][5] == rmvSong ){
                    Musics.remove(i);
                    getQueueAsArrayAndUpdate();
                    break;
                }
            }
             */

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Updates the queue list. It receives the song as array and updates the queue.
     * It is called when a song is added or removed.
     */
    public void getQueueAsArrayAndUpdate() {
        this.queue = this.Musics.toArray(new String[this.Musics.size()][7]);
        this.window.updateQueueList(this.queue);
    }

    //</editor-fold>

    //<editor-fold desc="Controls">

    /**
     * This function is called when Play Now is clicked
     *
     * @throws JavaLayerException
     * @throws FileNotFoundException
     *
     */

    public void start() {

        try {

            // A new song will play, so if any song is playing, it will stop
            newPlay = true;

            // Get pressed song index
            int musicIdx = window.getSelectedIdx();

            // Update miniplayer
            window.updatePlayingSongInfo(Musics.get(musicIdx)[0], Musics.get(musicIdx)[1], Musics.get(musicIdx)[2]);

            currentSong = Songs.get(musicIdx);

            playerEnabled = true;
            playerPaused = false;


            // Enabling all the buttons, because a song is going to play
            window.setEnabledPlayPauseButton(playerEnabled);
            window.updatePlayPauseButtonIcon(playerPaused);
            window.setEnabledScrubber(playerEnabled);
            window.setEnabledStopButton(playerEnabled);
            window.setEnabledNextButton(playerEnabled);
            window.setEnabledPreviousButton(playerEnabled);
            window.setEnabledShuffleButton(playerEnabled);
            window.setEnabledRepeatButton(playerEnabled);

            // Decode and but the song on buffer
            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currentSong.getBufferedInputStream());

            // Skipping to frame 0, setting currentFrame to 0 and calling playing to start the song
            skipToFrame(0);
            newPlay = false;
            currentFrame = 0;
            playing(currentSong);

        } catch (JavaLayerException device) {
            device.printStackTrace();
        } catch (FileNotFoundException bitstream) {
            bitstream.printStackTrace();
        }

    }

    // getSongLength
    public float getSongLength(Song currentSong) {
        return currentSong.getMsLength();
    }

    // getSongMsPerFrame
    public float getSongMsPerFrame(Song currentSong) {
        return currentSong.getMsPerFrame();
    }


    /**
     * Responsible for playing the frames of the current Song.
     *
     * @param currentSong current playing song
     */
    public void playing(Song currentSong){

        // Thread start
        Thread t_playingSong = new Thread(new Runnable() {
            @Override
            public void run() {

                fullLength = getSongLength(currentSong);

                ms = getSongMsPerFrame(currentSong);
                ms = (int) ms;

                // window.setTime((int) (currentFrame * ms), (int) fullLength);

                /*
                Song reproduction happens the while below. It depends on the playerPaused and on the
                newPlay.
                */
                while (true && !playerPaused) {
                    lock.lock();
                    try {
                        if (!playNextFrame()){
                            next();
                        }

                        /*
                        newPlay is true when a new Song is going to play and the current HAS to stop.
                        For example: If next or previous is clicked, or Play Now, or the song ends.
                        */
                        if (newPlay) break;
                        // Jumping frames
                        currentFrame +=1;
                        // Updating time on miniplayer
                        window.setTime((int) (currentFrame * ms), (int) fullLength);
                    } catch (JavaLayerException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    ;
                    lock.unlock();
                }
            }
        });
        t_playingSong.start();

    }

    /**
     * Stops the reproductions of the song and resets to the state of Non-playing.
     *
     */
    public void stop() {
        if (!playerPaused) {
            playerPaused = true;
            window.updatePlayPauseButtonIcon(playerPaused);
            window.resetMiniPlayer();
            window.setEnabledScrubberArea(false);
            window.setEnabledPreviousButton(false);
            window.setEnabledNextButton(false);
            window.setEnabledRepeatButton(false);
            window.setEnabledShuffleButton(false);
            window.setEnabledStopButton(false);
        }
    }


    /**
     * Either pauses or resumes the current playing song with the boolean PlayerPaused
     */
    public void PlayPause() {

        if (!playerPaused) {
            playerPaused = true;
            window.updatePlayPauseButtonIcon(playerPaused); // Updating button state
        } else {
            playerPaused = false;
            window.updatePlayPauseButtonIcon(playerPaused); // Updating button state
            playing(currentSong); // Resuming the song
        }
    }

    /**
     * Jumps to next song according to the Musics and Songs array.
     *
     *
     * @throws JavaLayerException
     * @throws FileNotFoundException
     */
    public void next() throws JavaLayerException, FileNotFoundException {
        newPlay = true;
        int musicIdx = 0;
        int actual = Songs.indexOf(currentSong);

        // If the song is not in the last position, get the next song's index
        if (actual != Songs.size() - 1) {
            musicIdx = actual + 1;
        }

        /*
        Checking if the song is the last one and repeat is pressed OR if the song is not the last one.

        If none of these are true, it means that the song is the last one and repeat is no pressed, which
        means the player will stop.
        */
        if (actual == Songs.size() - 1 && repeat == true || actual != Songs.size() - 1) {
            currentSong = Songs.get(musicIdx);
            window.updatePlayingSongInfo(Musics.get(musicIdx)[0], Musics.get(musicIdx)[1], Musics.get(musicIdx)[2]);

            playerPaused = false;
            window.updatePlayPauseButtonIcon(playerPaused);

            // Setting new song's info, skipping to frame 0 and setting currentFrame to 0.
            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currentSong.getBufferedInputStream());

            skipToFrame(0);
            newPlay = false;
            currentFrame = 0;
            // Calling playing to start next song
            playing(currentSong);
        }

    }

    /**
     * Jumps to previous song according to the Music and Songs array.
     *
     *
     * @throws JavaLayerException
     * @throws FileNotFoundException
     */
    public void previous() throws JavaLayerException, FileNotFoundException {
        newPlay = true;
        int musicIdx = Songs.size() - 1;
        int actual = Songs.indexOf(currentSong);
        if (actual != 0) {

            musicIdx = actual - 1;
        }

        window.updatePlayingSongInfo(Musics.get(musicIdx)[0], Musics.get(musicIdx)[1], Musics.get(musicIdx)[2]);

        currentSong = Songs.get(musicIdx);

        playerPaused = false;
        window.updatePlayPauseButtonIcon(playerPaused);


        // Setting new song's info, skipping to frame 0 and setting currentFrame to 0.
        device = FactoryRegistry.systemRegistry().createAudioDevice();
        device.open(decoder = new Decoder());
        bitstream = new Bitstream(currentSong.getBufferedInputStream());

        skipToFrame(0);
        newPlay = false;
        currentFrame = 0;
        // Calling playing to start next song
        playing(currentSong);
    }

    /**
     * Changes to shuffle or back to normal.
     *
     *
     * @throws JavaLayerException
     * @throws FileNotFoundException
     */
    public void randomSong() throws JavaLayerException, FileNotFoundException {

        if(shuffle == false){
            shuffle = true;

            int idxCurrent = Songs.indexOf(currentSong);

            aux.clear();
            aux.addAll(Songs); // Array that is going to keep original order

            Songs.remove(idxCurrent); // Removing current song to insert in the beginning afterwards

            Collections.shuffle(Songs); // Shuffling list
            Songs.add(0, currentSong); // Inserting current song to start

            Musics.clear(); // Clearing Musics array to update it with random order

            // This loop is responsible for refilling Musics array just as add to queue does.
            for (int i = 0; i < Songs.size(); i++) {

                String[] songInfo = Songs.get(i).getDisplayInfo();

                Musics.add(songInfo);

            }

            System.out.println(Musics);
        }
        else{

            shuffle = false;

            Songs.clear();

            // Copying all of the songs in aux to Songs
            Songs.addAll(aux);

            Musics.clear();

            // This loop is responsible for refilling Musics array just as add to queue does.
            for (int i = 0; i < Songs.size(); i++) {

                String[] songInfo = Songs.get(i).getDisplayInfo();

                Musics.add(songInfo);

            }
        }


    }
    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>
}

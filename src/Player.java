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

    private ArrayList<String[]> Musics = new ArrayList<String[]>();

    private String[][] queue = {};

    private ArrayList<Song> Songs = new ArrayList<Song>();

    private ArrayList<Song> aux = new ArrayList<Song>(); // Array auxiliar para shuffle

    public Player() {

        // Setamos os botões, os que ainda não foram implementados, chamamos a função
        // next() para preencher escopo e deixamos os botões desabilitados

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
                playerPaused = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {


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

        int framesToSkip = newFrame;
        boolean condition = true;
        while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    public void addToQueue() {

        try {
            Song newSong = window.getNewSong();

            if (newSong != null) {
                Songs.add(newSong);

                String[] songInfo = newSong.getDisplayInfo();

                Musics.add(songInfo);

                getQueueAsArrayAndUpdate();
            }



        }
        catch(InvalidDataException | BitstreamException | UnsupportedTagException | IOException e){
            System.out.println(e);
        }
    }


    public void removeFromQueue() {
        try {

            int rmvSong = window.getSelectedIdx();

            Musics.remove(rmvSong);
            Songs.remove(rmvSong);
            getQueueAsArrayAndUpdate();

            System.out.println("Música removida");

            /*

            Forma mais ineficiente de encontrar o indice da musica que será removida, FUNCIONA!

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

    public void getQueueAsArrayAndUpdate() {
        this.queue = this.Musics.toArray(new String[this.Musics.size()][7]);
        this.window.updateQueueList(this.queue);
    }

    //</editor-fold>

    //<editor-fold desc="Controls">

    public void start() {

        try {

            newPlay = true;

            int musicIdx = window.getSelectedIdx();

            window.updatePlayingSongInfo(Musics.get(musicIdx)[0], Musics.get(musicIdx)[1], Musics.get(musicIdx)[2]);

            currentSong = Songs.get(musicIdx);

            playerEnabled = true;
            playerPaused = false;


            // Ativando os botões
            window.setEnabledPlayPauseButton(playerEnabled);
            window.updatePlayPauseButtonIcon(playerPaused);
            window.setEnabledScrubber(playerEnabled);
            window.setEnabledStopButton(playerEnabled);
            window.setEnabledNextButton(playerEnabled);
            window.setEnabledPreviousButton(playerEnabled);
            window.setEnabledShuffleButton(playerEnabled);
            window.setEnabledRepeatButton(playerEnabled);

            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currentSong.getBufferedInputStream());

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

    public void playing(Song currentSong){

        Thread t_playingSong = new Thread(new Runnable() {
            @Override
            public void run() {

                fullLength = getSongLength(currentSong);

                // Adicionando o tempo no início, mas n atualiza a cada segundo

                ms = getSongMsPerFrame(currentSong);
                ms = (int) ms;

                // window.setTime((int) (currentFrame * ms), (int) fullLength);

                while (true && !playerPaused) {
                    lock.lock();
                    try {
                        if (!playNextFrame()){
                            next();
                        }
                        if (newPlay) break;
                        currentFrame +=1;
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

    //public void Play

    public void PlayPause() {

        if (!playerPaused) {
            playerPaused = true;
            window.updatePlayPauseButtonIcon(playerPaused);
        } else {
            playerPaused = false;
            window.updatePlayPauseButtonIcon(playerPaused);
            playing(currentSong);
        }
    }


    public void next() throws JavaLayerException, FileNotFoundException {
        newPlay = true;
        int musicIdx = 0;
        int actual = Songs.indexOf(currentSong);

        if (actual != Songs.size() - 1) {
            musicIdx = actual + 1;
        }
        if (actual == Songs.size() - 1 && repeat == true || actual != Songs.size() - 1) {
            currentSong = Songs.get(musicIdx);
            window.updatePlayingSongInfo(Musics.get(musicIdx)[0], Musics.get(musicIdx)[1], Musics.get(musicIdx)[2]);

            playerPaused = false;
            window.updatePlayPauseButtonIcon(playerPaused);

            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(currentSong.getBufferedInputStream());

            skipToFrame(0);
            newPlay = false;
            currentFrame = 0;
            playing(currentSong);
        }

    }

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

        device = FactoryRegistry.systemRegistry().createAudioDevice();
        device.open(decoder = new Decoder());
        bitstream = new Bitstream(currentSong.getBufferedInputStream());

        skipToFrame(0);
        newPlay = false;
        currentFrame = 0;
        playing(currentSong);
    }
    /*
    public void playAgain() throws JavaLayerException, FileNotFoundException {

        newPlay = true;
        int actual = Songs.indexOf(currentSong);

        window.updatePlayingSongInfo(Musics.get(actual)[0], Musics.get(actual)[1], Musics.get(actual)[2]);

        playerPaused = false;
        window.updatePlayPauseButtonIcon(playerPaused);

        device = FactoryRegistry.systemRegistry().createAudioDevice();
        device.open(decoder = new Decoder());
        bitstream = new Bitstream(currentSong.getBufferedInputStream());

        skipToFrame(0);
        newPlay = false;
        currentFrame = 0;
        playing(currentSong);

    }*/

    public void randomSong() throws JavaLayerException, FileNotFoundException {

        if(shuffle == false){
            shuffle = true;

            int idxCurrent = Songs.indexOf(currentSong);

            aux.clear();
            aux.addAll(Songs); // Copying array

            System.out.println(Musics);
            System.out.println(Songs);

            Songs.remove(idxCurrent);
            Musics.remove(idxCurrent);

            Collections.shuffle(Songs);
            Songs.add(0, currentSong); // Adding music to start

            System.out.println(Songs);

            Musics.clear();

            for (int i = 0; i < Songs.size(); i++) {

                String[] songInfo = Songs.get(i).getDisplayInfo();

                Musics.add(songInfo);

            }

            System.out.println(Musics);
        }
        else{

            shuffle = false;

            Songs.clear();

            Songs.addAll(aux);

            Musics.clear();

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

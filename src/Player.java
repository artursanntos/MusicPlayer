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
    private Song currentSong;
    private int currentFrame = 0;
    private int newFrame;

    private final Lock lock = new ReentrantLock();

    private ArrayList<String[]> Musics = new ArrayList<String[]>();

    private String[][] queue = {};

    private ArrayList<Song> Songs = new ArrayList<Song>();

    public Player(String filePath) {

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
                PlayPause();
            }
        };

        ActionListener buttonListenerPrevious = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                previous();
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
                next();
            }
        };

        ActionListener buttonListenerRepeat = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlayPause();
            }
        };

        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {

            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

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
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    public void addToQueue() {

        Thread t_addSong = new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    lock.lock();

                    Song newSong = window.getNewSong();

                    Songs.add(newSong);

                    String[] songInfo = newSong.getDisplayInfo();

                    Musics.add(songInfo);

                    getQueueAsArrayAndUpdate();

                }
                catch(InvalidDataException | BitstreamException | UnsupportedTagException | IOException e){
                    System.out.println(e);
                }

                finally {
                    lock.unlock();
                }

            }
        });

        t_addSong.start();
    }

    public void removeFromQueue() {
        Thread t_rmvFromQueue = new Thread(new Runnable() {
            @Override
            public void run() {


                try {
                    lock.lock();

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

                }

                finally {
                    lock.unlock();
                }

            }
        });

        t_rmvFromQueue.start();

    }

    public void getQueueAsArrayAndUpdate() {
        this.queue = this.Musics.toArray(new String[this.Musics.size()][8]);
        this.window.updateQueueList(this.queue);
    }

    //</editor-fold>

    //<editor-fold desc="Controls">

    public void start() {
        Thread t_PlayNow = new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    lock.lock();

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

                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(currentSong.getBufferedInputStream());

                    playing();

                } catch (JavaLayerException device) {
                    device.printStackTrace();
                } catch (FileNotFoundException bitstream) {
                    bitstream.printStackTrace();
                } finally {
                    lock.unlock();
                }

            }
        });

        t_PlayNow.start();

    }

    // getSongLength
    public float getSongLength() {
        int musicIdx = window.getSelectedIdx();
        return Songs.get(musicIdx).getMsLength();
    }

    public void playing(){

        Thread t_playingSong = new Thread(new Runnable() {
            @Override
            public void run() {
                float fullLength = getSongLength();

                // Adicionando o tempo no início, mas n atualiza a cada segundo
                int currentTime = 0;
                window.setTime(currentTime, (int) fullLength);

                while (true && !playerPaused) {
                    try {
                        if (!playNextFrame()) break;
                        // window.setTime(currentTime, (int) fullLength);
                    } catch (JavaLayerException e) {
                        e.printStackTrace();
                    };
                }
            }
        });
        t_playingSong.start();

    }

    public void stop() {
    }

    //public void Play

    public void PlayPause() {

        if (!playerPaused) {
            playerPaused = true;
            window.updatePlayPauseButtonIcon(playerPaused);
        } else {
            playerPaused = false;
            window.updatePlayPauseButtonIcon(playerPaused);
            playing();
        }
    }


    public void next() {
    }

    public void previous() {
    }
    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>
}

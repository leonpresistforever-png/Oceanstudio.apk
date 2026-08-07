import { motion } from 'framer-motion';

export default function PlaygroundVisual() {
  return (
    <div className="pg-visual">
      <video
        className="pg-visual-video"
        autoPlay
        muted
        loop
        playsInline
        poster="https://images.pexels.com/photos/1103970/pexels-photo-1103970.jpeg?auto=compress&cs=tinysrgb&w=1920"
      >
        <source
          src="https://videos.pexels.com/video-files/854478/854478-hd_1920_1080_25fps.mp4"
          type="video/mp4"
        />
      </video>
      <div className="pg-visual-fog" />
      <div className="pg-visual-orb" aria-hidden>
        <div className="pg-orb-core" />
        <div className="pg-orb-ring pg-orb-ring-1" />
        <div className="pg-orb-ring pg-orb-ring-2" />
        <div className="pg-orb-ring pg-orb-ring-3" />
      </div>
      <motion.div
        className="pg-visual-label"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
      >
        <span className="pg-visual-badge">PLAYGROUND</span>
        <h2>Your creative tool ground</h2>
        <p>Agents · Media · Music · 3D · Recording · Active Bot</p>
      </motion.div>
    </div>
  );
}

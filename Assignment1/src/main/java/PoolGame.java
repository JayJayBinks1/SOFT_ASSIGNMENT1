import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.scene.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.canvas.GraphicsContext;

import javafx.util.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;


public class PoolGame extends Application {
    
    private static String[] arguments;

    public static void main(String[] args) {
        arguments = args;
        launch(args);
    }

    public Point2D[] collide(Point2D posA, Point2D velA, double massA, Point2D posB, Point2D velB, double massB) {

        //calculate their mass ratio
        double mR = massB/massA;

        //calculate the axis of collision
        Point2D collisionVector = posB.subtract(posA);
        //System.out.println(collisionVector);
        collisionVector = collisionVector.normalize();
        //System.out.println(collisionVector);

        //the proportion of each balls velocity along the axis of collision
        double vA = collisionVector.dotProduct(velA);
        double vB = collisionVector.dotProduct(velB);

        //if balls are moving away from each other
        if (vA <= 0 && vB >= 0) {
            return null;
        }

        //The velocity of each ball after a collision can be found by solving the quadratic equation
        //given by equating momentum energy and energy before and after the collision and finding the
        //velocities that satisfy this
        //-(mR+1)x^2 2*(mR*vB+vA)x -((mR-1)*vB^2+2*vA*vB)=0
        //first we find the discriminant
        double a = -(mR + 1);
        double b = 2 * (mR * vB + vA);
        double c = -((mR - 1) * vB * vB + 2 * vA * vB);
        double discriminant = Math.sqrt(b * b - 4 * a * c);
        double root = (-b + discriminant)/(2 * a);
        //only one of the roots is the solution, the other pertains to the current velocities
        if (root - vB < 0.01) {
            root = (-b - discriminant)/(2 * a);
        }

        //The resulting changes in velocity for ball A and B
        Point2D deltaVA = collisionVector.multiply(mR * (vB - root));
        Point2D deltaVB = collisionVector.multiply(mR * (root - vB));

        Point2D newVels[] = new Point2D[2];

        Point2D actualVA = new Point2D(deltaVA.getX()/(massA+massB), deltaVA.getY()/(massA+massB));
        Point2D actualVB = new Point2D(deltaVB.getX()/(massA+massB), deltaVB.getY()/(massA+massB));

        newVels[0] = deltaVA;
        newVels[1] = deltaVB;

        return newVels;
    }

    private Line cue;
    private boolean isCueSet = false;
    boolean removeLine = false;

    @Override
    public void start(Stage primaryStage) {
        File file = new File(arguments[0]);
        JSONObject objects = null;
        try {
            FileReader reader = new FileReader(file);
            JSONParser parser = new JSONParser();
            objects = (JSONObject) parser.parse(reader);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        JSONObject tableContents = (JSONObject) objects.get("Table");
        Table table = new Table(tableContents);
        JSONObject ballList = (JSONObject) objects.get("Balls");
        JSONArray balls = (JSONArray) ballList.get("ball");

        ArrayList<Ball> poolBalls = new ArrayList<>();

        Director director = new Director();

        for (int i = 0; i < balls.size(); i++) {
            //System.out.println(balls.get(i));
            ConfigReader reader = ConfigReader.getReader(ObjectType.BALL, (JSONObject) balls.get(i));
            BallBuilder builder = new BallBuilder();
            director.buildBall(builder, reader.getContents());
            poolBalls.add(builder.getBall());
        }

        try {

            Rectangle tableShape = table.getShape();

            Canvas canvas = new Canvas(tableShape.getWidth()+150, tableShape.getHeight()+150);

            Pane pane = new Pane();  //The root of scene graph is a layout node
            // Creating a Scene by passing the group object, height and width
            Scene scene = new Scene(pane,tableShape.getWidth()+150,tableShape.getHeight()+150);
            //setting color to the scene
            scene.setFill(Color.BLACK);

            pane.getChildren().add(tableShape);

            Circle holes[] = table.getHoles();

            for (Circle hole : holes) {
                pane.getChildren().addAll(hole);
            }

            for (Ball ball : poolBalls) {
                pane.getChildren().add(ball.getBall());
            }

            pane.setOnMousePressed(event -> {
                isCueSet = false;
                cue = new Line(event.getX(), event.getY(), event.getX(), event.getY());
                cue.setStrokeWidth(5);
                cue.setStroke(Color.BURLYWOOD);
                pane.getChildren().add(cue);
            });

            pane.setOnMouseDragged(event -> {
                cue.setEndX(event.getX());
                cue.setEndY(event.getY());
            });

            pane.setOnMouseReleased(event -> {
                pane.getChildren().remove(cue);
                isCueSet = true;
            });

            //Setting the title to Stage.
            primaryStage.setTitle("Pool Game");

            //Adding the scene to Stage
            primaryStage.setScene(scene);
            //Displaying the contents of the stage
            primaryStage.show();
//            System.out.println(table.getFriction());
            AnimationTimer animator = new AnimationTimer() {
                @Override
                public void handle(long now) {

                    for (int i = 0; i < poolBalls.size(); i++) {
                        Ball ball = poolBalls.get(i);
                        ball.setxPosition((ball.getxPosition()+ball.getxVelocity()));
                        ball.setyPosition((ball.getyPosition()+ball.getyVelocity()));

                        if (ball.getxVelocity() == 0.0) {
                            ball.setxVelocity(0.000001);
                        }

                        if (ball.getyVelocity() == 0.0) {
                            ball.setyVelocity(0.000001);
                        }

                        for (Circle hole : holes) {
                            double deltaX = ball.getxPosition() - hole.getCenterX();
                            double deltaY = ball.getyPosition() - hole.getCenterY();
                            double distance = Math.sqrt((deltaX*deltaX) + (deltaY*deltaY));

                            if (distance <= 30) {
                                poolBalls.remove(i);
                                pane.getChildren().remove(ball.getBall());
//                                if (ball.getColour().equalsIgnoreCase("white")) {
//                                    stop();
//                                }
                            }
                        }

                        if (isCueSet && ball.getColour().equalsIgnoreCase("white") && ball.getxVelocity() == 0.000001 && ball.getyVelocity() == 0.000001) {

                            double deltaX = ball.getxPosition() - cue.getStartX();
                            double deltaY = ball.getyPosition() - cue.getStartY();
                            double distance = Math.sqrt((deltaX*deltaX) + (deltaY*deltaY));
                            if (distance <= 15) {

                                Point2D ballPos = new Point2D(ball.getxPosition(), ball.getyPosition());
                                Point2D ballVel = new Point2D(ball.getxVelocity(), ball.getyVelocity());
                                double ballMass = ball.getMass();

                                Point2D otherPos = new Point2D(cue.getEndX(), cue.getEndY());
                                double xVelocity = 0;
                                double yVelocity = 0;

                                if (cue.getEndX() >= ball.getxPosition()) {
                                    xVelocity = -1;
                                }
                                else {
                                    xVelocity = 1;
                                }

                                if (cue.getEndY() >= ball.getyPosition()) {
                                    yVelocity = -1;
                                }
                                else {
                                    yVelocity = 1;
                                }

                                Point2D otherVel = new Point2D(xVelocity, yVelocity);
                                double otherMass = 1;

                                Point2D newVels[] = collide(ballPos, ballVel, ballMass, otherPos, otherVel, otherMass);

                                if (newVels == null) {
                                    continue;
                                }

                                ballVel = newVels[0];

                                ball.setxVelocity(ballVel.getX());
                                ball.setyVelocity(ballVel.getY());

                                isCueSet = false;
                                //pane.getChildren().remove(cue);
                                removeLine = true;

                            }
                        }

                        for (int j = 0; j < poolBalls.size(); j++) {
                            if (i == j) {
                                continue;
                            }

                            Ball otherBall = poolBalls.get(j);

                            double deltaX = ball.getxPosition() - otherBall.getxPosition();
                            double deltaY = ball.getyPosition() - otherBall.getyPosition();
                            double distance = Math.sqrt((deltaX*deltaX) + (deltaY*deltaY));

                            if (distance <= 30) {
                                Point2D ballPos = new Point2D(ball.getxPosition(), ball.getyPosition());
                                Point2D ballVel = new Point2D(ball.getxVelocity(), ball.getyVelocity());
                                double ballMass = ball.getMass();

                                Point2D otherPos = new Point2D(otherBall.getxPosition(), otherBall.getyPosition());
                                Point2D otherVel = new Point2D(otherBall.getxVelocity(), otherBall.getyVelocity());
                                double otherMass = otherBall.getMass();

                                Point2D newVels[] = collide(ballPos, ballVel, ballMass, otherPos, otherVel, otherMass);

                                if (newVels == null) {
                                    continue;
                                }

                                ballVel = newVels[0];
                                otherVel = newVels[1];

                                ball.setxVelocity(ballVel.getX());
                                ball.setyVelocity(ballVel.getY());

                                otherBall.setxVelocity(otherVel.getX());
                                otherBall.setyVelocity(otherVel.getY());

                            }
                        }

                        if ((ball.getxPosition() + 15 + ball.getxVelocity()) >= (100+table.getWidth()-50) || (ball.getxPosition() + 15 + ball.getxVelocity()) <= (125)) {
                            double xVelocity = ball.getxVelocity();
                            ball.setxVelocity(xVelocity *= -1);
                        }

                        if ((ball.getyPosition() + 15 + ball.getyVelocity()) >= (100+table.getHeight()-50) || (ball.getyPosition() + 15 + ball.getyVelocity()) <= (125)) {
                            double yVelocity = ball.getyVelocity();
                            ball.setyVelocity(yVelocity *= -1);
                        }


                        ball.getBall().setCenterX(ball.getxPosition());
                        ball.getBall().setCenterY(ball.getyPosition());

                        if (ball.getxVelocity() > 0) {
                            ball.setxVelocity(ball.getxVelocity() + (table.getFriction()*-(0.001)));
                        }

                        else if (ball.getxVelocity() < 0) {
                            ball.setxVelocity(ball.getxVelocity() - (table.getFriction()*-(0.001)));
                        }

                        if (ball.getxVelocity() <= 0.001 && ball.getxVelocity() > -0.001) {
                            ball.setxVelocity(0.000001);
                        }

                        if (ball.getyVelocity() > 0) {
                            ball.setyVelocity(ball.getyVelocity() + (table.getFriction()*-(0.001)));
                        }

                        else if (ball.getyVelocity() < 0) {
                            ball.setyVelocity(ball.getyVelocity() - (table.getFriction()*-(0.001)));
                        }

                        if (ball.getyVelocity() <= 0.001 && ball.getyVelocity() > -0.001) {
                            ball.setyVelocity(0.000001);
                        }

                    }
                }
            };

            animator.start();

        }  catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}